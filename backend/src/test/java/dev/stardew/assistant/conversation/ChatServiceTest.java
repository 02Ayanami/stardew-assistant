package dev.stardew.assistant.conversation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.ai.*;
import dev.stardew.assistant.common.*;
import dev.stardew.assistant.tools.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

class ChatServiceTest {
  final String id = UUID.randomUUID().toString();
  final ChatMapper db = mock(ChatMapper.class);
  final ModelGateway model = mock(ModelGateway.class);
  final ToolService tools = mock(ToolService.class);
  final ObjectMapper json = new ObjectMapper();
  ChatService service;
  MutableClock clock = new MutableClock();

  @BeforeEach
  void setup() throws Exception {
    when(db.session(id)).thenReturn(new ChatSession(id, "新对话", Instant.EPOCH, Instant.EPOCH));
    when(db.recent(id)).thenReturn(List.of());
    doAnswer(
            inv -> {
              ChatMessage m = inv.getArgument(0);
              m.id = 5L;
              return null;
            })
        .when(db)
        .insert(any());
    var manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    service =
        new ChatService(
            db,
            model,
            new ToolDefinitions(json),
            tools,
            new ToolInputGuard(json),
            json,
            new TransactionTemplate(manager),
            clock);
  }

  AssistantMessage tool(String name, String args) {
    return AssistantMessage.builder()
        .content("")
        .toolCalls(List.of(new AssistantMessage.ToolCall("one", "function", name, args)))
        .build();
  }

  @Test
  void normalAnswerPersistsOneWholePair() {
    when(model.call(anyList(), anyList(), any())).thenReturn(new AssistantMessage("你好，农场主"));
    assertThat(service.chat(id, "你好").answer()).contains("农场主");
    verify(db, times(2)).insert(any());
  }

  @Test
  void nativeToolCallsAreSequentialAndReturned() {
    when(model.call(anyList(), anyList(), any()))
        .thenReturn(
            tool("listCrops", "{}"),
            tool("queryGameData", "{\"idOrName\":\"防风草\"}"),
            new AssistantMessage("查询完成"));
    when(tools.execute(anyString(), anyMap(), any()))
        .thenAnswer(inv -> ToolResult.ok(Map.of("count", 1), List.of()));
    assertThat(service.chat(id, "查一下防风草").toolResults()).hasSize(2);
    var order = inOrder(tools);
    order.verify(tools).execute(eq("listCrops"), anyMap(), any());
    order.verify(tools).execute(eq("queryGameData"), anyMap(), any());
  }

  @Test
  void missingConditionsNeverExecuteCalculator() {
    when(model.call(anyList(), anyList(), any()))
        .thenReturn(
            tool("calculateCropProfit", "{\"quantity\":2,\"budget\":40}"),
            new AssistantMessage("请提供日期和预算"));
    assertThat(service.chat(id, "算一下防风草收益").toolResults().get(0).result().status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
    verifyNoInteractions(tools);
  }

  @Test
  void modelFailureDoesNotPersistAndReleasesLock() {
    when(model.call(anyList(), anyList(), any()))
        .thenThrow(new AppException(502, "MODEL_FAILED", "失败"))
        .thenReturn(new AssistantMessage("恢复"));
    assertThatThrownBy(() -> service.chat(id, "你好")).isInstanceOf(AppException.class);
    verify(db, never()).insert(any());
    assertThat(service.chat(id, "重试").answer()).isEqualTo("恢复");
  }

  @Test
  void limitsModelRoundsWithoutSavingPartialHistory() {
    when(model.call(anyList(), anyList(), any())).thenReturn(tool("listCrops", "{}"));
    when(tools.execute(anyString(), anyMap(), any()))
        .thenAnswer(inv -> ToolResult.ok(Map.of(), List.of()));
    assertThatThrownBy(() -> service.chat(id, "列作物"))
        .isInstanceOfSatisfying(
            AppException.class, e -> assertThat(e.code).isEqualTo("MODEL_LIMIT"));
    verify(model, times(8)).call(anyList(), anyList(), any());
    verify(db, never()).insert(any());
  }

  @Test
  void limitsTotalToolCalls() {
    var calls = new ArrayList<AssistantMessage.ToolCall>();
    for (int i = 0; i < 13; i++)
      calls.add(new AssistantMessage.ToolCall("t" + i, "function", "listCrops", "{}"));
    when(model.call(anyList(), anyList(), any()))
        .thenReturn(AssistantMessage.builder().content("").toolCalls(calls).build());
    when(tools.execute(anyString(), anyMap(), any()))
        .thenAnswer(inv -> ToolResult.ok(Map.of(), List.of()));
    assertThatThrownBy(() -> service.chat(id, "查询"))
        .isInstanceOfSatisfying(
            AppException.class, e -> assertThat(e.code).isEqualTo("TOOL_LIMIT"));
    verify(tools, times(12)).execute(anyString(), anyMap(), any());
  }

  @Test
  void deadlinePreventsPersistingLateResponse() {
    when(model.call(anyList(), anyList(), any()))
        .thenAnswer(
            inv -> {
              clock.now = clock.now.plusSeconds(121);
              return new AssistantMessage("太迟");
            });
    assertThatThrownBy(() -> service.chat(id, "你好"))
        .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.status).isEqualTo(504));
    verify(db, never()).insert(any());
  }

  @Test
  void concurrentSameSessionReturnsConflict() throws Exception {
    var entered = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    when(model.call(anyList(), anyList(), any()))
        .thenAnswer(
            inv -> {
              entered.countDown();
              finish.await(5, TimeUnit.SECONDS);
              return new AssistantMessage("完成");
            });
    var pool = Executors.newSingleThreadExecutor();
    try {
      var pending = pool.submit(() -> service.chat(id, "第一个"));
      assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> service.chat(id, "第二个"))
          .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.status).isEqualTo(409));
      finish.countDown();
      pending.get(3, TimeUnit.SECONDS);
    } finally {
      finish.countDown();
      pool.shutdownNow();
    }
  }

  static class MutableClock extends Clock {
    Instant now = Instant.EPOCH;

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }
}
