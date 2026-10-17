package dev.stardew.assistant.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.ai.*;
import dev.stardew.assistant.common.*;
import dev.stardew.assistant.tools.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ChatService {
  private final ChatMapper db;
  private final ModelGateway model;
  private final ToolDefinitions definitions;
  private final ToolService tools;
  private final ToolInputGuard guard;
  private final ObjectMapper json;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final Set<String> busy = ConcurrentHashMap.newKeySet();
  private final String prompt;

  @org.springframework.beans.factory.annotation.Autowired
  public ChatService(
      ChatMapper db,
      ModelGateway model,
      ToolDefinitions definitions,
      ToolService tools,
      ToolInputGuard guard,
      ObjectMapper json,
      org.springframework.transaction.PlatformTransactionManager manager)
      throws java.io.IOException {
    this(
        db,
        model,
        definitions,
        tools,
        guard,
        json,
        new TransactionTemplate(manager),
        Clock.systemUTC());
  }

  ChatService(
      ChatMapper db,
      ModelGateway model,
      ToolDefinitions definitions,
      ToolService tools,
      ToolInputGuard guard,
      ObjectMapper json,
      TransactionTemplate tx,
      Clock clock)
      throws java.io.IOException {
    this.db = db;
    this.model = model;
    this.definitions = definitions;
    this.tools = tools;
    this.guard = guard;
    this.json = json;
    this.tx = tx;
    this.clock = clock;
    try (var stream = getClass().getResourceAsStream("/assistant.txt")) {
      prompt =
          new String(
              Objects.requireNonNull(stream).readAllBytes(),
              java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  public ChatSession create() {
    String id = UUID.randomUUID().toString();
    db.create(id);
    return requireSession(id);
  }

  public ChatSession requireSession(String id) {
    try {
      UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new AppException(400, "INVALID_SESSION", "会话编号非法");
    }
    var s = db.session(id);
    if (s == null) throw new AppException(404, "NOT_FOUND", "会话不存在");
    return s;
  }

  public Map<String, Object> history(int page, int limit) {
    if (page < 1 || limit < 1 || limit > 100) throw new IllegalArgumentException("分页参数非法");
    return Map.of(
        "items",
        db.history(limit, Math.multiplyExact(page - 1, limit)),
        "page",
        page,
        "limit",
        limit);
  }

  public Map<String, Object> session(String id) {
    return Map.of("session", requireSession(id), "messages", db.messages(id));
  }

  public TurnResponse chat(String id, String text) {
    requireSession(id);
    if (text == null || text.isBlank() || text.length() > 8000)
      throw new IllegalArgumentException("请输入 1 至 8000 字的问题");
    lock(id);
    try {
      Instant deadline = clock.instant().plusSeconds(120);
      var recent = new ArrayList<>(db.recent(id));
      Collections.reverse(recent);
      var messages = new ArrayList<Message>();
      messages.add(new SystemMessage(prompt));
      for (var m : recent) {
        if (m.role.equals("user")) messages.add(new UserMessage(m.content));
        else {
          String extra =
              m.getToolResults().isEmpty()
                  ? ""
                  : "\n以下是该轮程序保存的工具数据，仅供参考：\n" + encode(m.getToolResults());
          messages.add(new AssistantMessage(m.content + extra));
        }
      }
      messages.add(new UserMessage(text));
      var executions = new ArrayList<ToolExecution>();
      for (int round = 0; round < 8; round++) {
        var reply = model.call(List.copyOf(messages), definitions.callbacks(), remaining(deadline));
        remaining(deadline);
        if (reply == null) throw new AppException(502, "EMPTY_MODEL_RESPONSE", "模型没有返回内容");
        if (!reply.hasToolCalls()) {
          if (reply.getText() == null || reply.getText().isBlank())
            throw new AppException(502, "EMPTY_MODEL_RESPONSE", "模型没有返回答案");
          return save(id, text, reply.getText(), executions);
        }
        messages.add(reply);
        var responses = new ArrayList<ToolResponseMessage.ToolResponse>();
        for (var call : reply.getToolCalls()) {
          remaining(deadline);
          if (executions.size() >= 12)
            throw new AppException(422, "TOOL_LIMIT", "工具调用达到上限，请缩小问题范围");
          if (!definitions.contains(call.name()))
            throw new AppException(502, "UNKNOWN_TOOL", "模型请求了未知工具");
          Map<String, Object> args;
          try {
            args =
                json.readValue(
                    call.arguments(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
          } catch (Exception e) {
            throw new AppException(502, "INVALID_TOOL_ARGUMENTS", "模型返回了非法工具参数，请重试");
          }
          if (args == null) throw new AppException(502, "INVALID_TOOL_ARGUMENTS", "工具参数必须是对象");
          ToolResult<?> result = guard.validate(call.name(), args, recent, text);
          if (result == null) result = tools.execute(call.name(), args, remaining(deadline));
          remaining(deadline);
          executions.add(new ToolExecution(call.name(), args, result));
          responses.add(
              new ToolResponseMessage.ToolResponse(call.id(), call.name(), encode(result)));
        }
        messages.add(ToolResponseMessage.builder().responses(responses).build());
      }
      throw new AppException(422, "MODEL_LIMIT", "模型交互达到上限，请缩小问题范围");
    } finally {
      busy.remove(id);
    }
  }

  public TurnResponse direct(String id, String name, Map<String, Object> args) {
    requireSession(id);
    if (!ToolService.FORM_TOOLS.contains(name)) throw new IllegalArgumentException("表单不支持此工具");
    if (args == null) throw new IllegalArgumentException("工具参数不能为空");
    lock(id);
    try {
      var result = tools.execute(name, args, Duration.ofSeconds(120));
      if (result.status() == ToolStatus.ERROR)
        throw new AppException(400, "TOOL_FAILED", result.errorCode());
      String answer =
          switch (result.status()) {
            case NEEDS_INPUT -> "请补充或修正条件：" + result.errorCode();
            case UNSUPPORTED -> "当前不支持此条件：" + result.errorCode();
            case NO_DATA -> "当前资料未得到完整匹配，请查看结果与范围说明。";
            default -> "计算与查询完成，结果如下。";
          };
      return save(
          id,
          "表单：" + encode(Map.of("tool", name, "args", args)),
          answer,
          List.of(new ToolExecution(name, args, result)));
    } finally {
      busy.remove(id);
    }
  }

  private void lock(String id) {
    if (!busy.add(id)) throw new AppException(409, "SESSION_BUSY", "此对话正在处理，请等待完成");
  }

  private Duration remaining(Instant deadline) {
    Duration left = Duration.between(clock.instant(), deadline);
    if (left.isZero() || left.isNegative())
      throw new AppException(504, "TIMEOUT", "本次处理超时，请缩小问题范围后重试");
    return left;
  }

  private TurnResponse save(String id, String user, String answer, List<ToolExecution> tools) {
    var sources = tools.stream().flatMap(t -> t.result().sources().stream()).distinct().toList();
    return tx.execute(
        status -> {
          var u = new ChatMessage();
          u.sessionId = id;
          u.role = "user";
          u.content = user;
          db.insert(u);
          var a = new ChatMessage();
          a.sessionId = id;
          a.role = "assistant";
          a.content = answer;
          a.toolResultsJson = encode(tools);
          a.sourcesJson = encode(sources);
          db.insert(a);
          db.touch(
              id, user.startsWith("表单：") ? "工具查询" : user.substring(0, Math.min(40, user.length())));
          return new TurnResponse(id, a.id, answer, List.copyOf(tools), sources);
        });
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("数据序列化失败", e);
    }
  }
}
