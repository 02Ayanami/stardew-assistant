package dev.stardew.assistant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.common.*;
import dev.stardew.assistant.conversation.ChatMessage;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ToolInputGuard {
  private final ObjectMapper json;

  public ToolInputGuard(ObjectMapper json) {
    this.json = json;
  }

  public ToolResult<?> validate(
      String tool, Map<String, Object> args, List<ChatMessage> history, String latest) {
    try {
      return validateEvidence(tool, args, history, latest);
    } catch (IllegalArgumentException e) {
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, "参数格式需要修正：" + e.getMessage());
    }
  }

  private ToolResult<?> validateEvidence(
      String tool, Map<String, Object> args, List<ChatMessage> history, String latest) {
    if (!Set.of("calculateCropProfit", "expandMaterialRequirements", "getBundleMissing")
        .contains(tool)) return null;
    var confirmed = new LinkedHashMap<String, Object>();
    var human = new ArrayList<String>();
    String previous = "";
    String wanted = topicForTool(tool);
    for (var m : history) {
      if (!m.role.equals("user")) {
        previous = m.content;
        continue;
      }
      String topic = topic(m.content);
      if (topic != null && !topic.equals(wanted)) {
        confirmed.clear();
        human.clear();
        previous = "";
        continue;
      }
      if (m.content.startsWith("表单：")) {
        try {
          var form = json.readTree(m.content.substring(m.content.indexOf('{')));
          if (tool.equals(form.path("tool").asText())) {
            confirmed.clear();
            confirmed.putAll(json.convertValue(form.path("args"), Map.class));
            human.add("表单：" + form.path("args").toString());
          } else {
            confirmed.clear();
            human.clear();
            previous = "";
          }
        } catch (Exception ignored) {
          confirmed.clear();
          human.clear();
          previous = "";
        }
      } else collect(args, m.content, previous, human, confirmed);
    }
    String currentTopic = topic(latest);
    if (currentTopic != null && !currentTopic.equals(wanted)
        || latest.matches("(?s).*(换个问题|另外一个|重新开始|新问题).*")) {
      confirmed.clear();
      human.clear();
    }
    collect(args, latest, previous, human, confirmed);
    var required =
        tool.equals("calculateCropProfit")
            ? List.of("quantity", "budget", "start", "end")
            : tool.equals("expandMaterialRequirements") ? List.of("quantity") : List.<String>of();
    for (String key : required) {
      if (args.get(key) == null
          || confirmed.get(key) == null
          || !(Set.of("quantity", "budget").contains(key)
              ? Objects.equals(
                  ExactInputs.number(args.get(key)), ExactInputs.number(confirmed.get(key)))
              : json.valueToTree(args.get(key)).equals(json.valueToTree(confirmed.get(key)))))
        return ToolResult.fail(ToolStatus.NEEDS_INPUT, "请明确提供或确认：" + key + "；不能使用模型猜测的值");
    }
    if (tool.equals("expandMaterialRequirements")
        && args.get("owned") instanceof Map<?, ?> stock
        && !stock.isEmpty()
        && human.stream().noneMatch(t -> t.matches("(?s).*(库存|已有|背包|表单).*")))
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, "未声明库存，不能使用模型猜测的已有材料");
    if (tool.equals("getBundleMissing")) {
      String text = String.join("\n", human);
      if (args.containsKey("gold")) {
        Long stated =
            confirmed.get("gold") == null ? null : ExactInputs.number(confirmed.get("gold"));
        for (String message : human) {
          Long value = NumericGrounding.requested(message, "budget");
          if (value != null) stated = value;
        }
        if (stated == null || !Objects.equals(stated, ExactInputs.number(args.get("gold"))))
          return ToolResult.fail(ToolStatus.NEEDS_INPUT, "请明确提供可用金币，不能猜测金额");
      }
      if (args.containsKey("submitted") && !text.matches("(?s).*(已提交|已交|表单).*"))
        return ToolResult.fail(ToolStatus.NEEDS_INPUT, "请确认已提交物品；没有请明确说为空");
      if (args.containsKey("owned") && !text.matches("(?s).*(背包|库存|已有|表单).*"))
        return ToolResult.fail(ToolStatus.NEEDS_INPUT, "请确认未提交背包物品；没有请明确说为空");
    }
    return null;
  }

  private void collect(
      Map<String, Object> proposed,
      String text,
      String previous,
      List<String> human,
      Map<String, Object> confirmed) {
    human.add(text);
    boolean clarification = previous.matches("(?s).*(请|多少|几株|几个|预算|日期|起始|截止|？|[?]).*");
    var evidence = new InputEvidence(text, previous, List.copyOf(human), confirmed, clarification);
    var numbers = NumericGrounding.filter(proposed, evidence);
    for (String k : List.of("quantity", "budget"))
      if (numbers.containsKey(k)) confirmed.put(k, numbers.get(k));
    var dates = DateGrounding.filter(proposed, evidence);
    for (String k : List.of("start", "end"))
      if (dates.containsKey(k)) confirmed.put(k, dates.get(k));
  }

  private String topicForTool(String tool) {
    return tool.equals("calculateCropProfit")
        ? "crop"
        : tool.equals("expandMaterialRequirements") ? "material" : "bundle";
  }

  private String topic(String s) {
    if (s.matches("(?s).*(献祭|收集包).*")) return "bundle";
    if (s.matches("(?s).*(钓鱼|钓什么|鱼类).*")) return "fish";
    if (s.matches("(?s).*(送礼|喜欢什么|最爱).*")) return "gift";
    if (s.matches("(?s).*(制作|合成|材料).*")) return "material";
    if (s.matches("(?s).*(种植|种[0-9一二两三四五六七八九十]+|作物|收益).*")) return "crop";
    return null;
  }
}
