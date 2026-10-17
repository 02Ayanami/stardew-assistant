package dev.stardew.assistant.ai;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.conversation.ChatMessage;
import java.util.*;
import org.junit.jupiter.api.Test;

class ToolInputGuardTest {
  final ToolInputGuard guard = new ToolInputGuard(new ObjectMapper());

  Map<String, Object> args() {
    return new LinkedHashMap<>(
        Map.of(
            "quantity",
            2,
            "budget",
            40,
            "start",
            Map.of("year", 1, "season", "spring", "day", 1),
            "end",
            Map.of("year", 1, "season", "spring", "day", 9)));
  }

  ChatMessage msg(String role, String text) {
    var m = new ChatMessage();
    m.role = role;
    m.content = text;
    return m;
  }

  @Test
  void acceptsOnlyExplicitValues() {
    assertThat(guard.validate("calculateCropProfit", args(), List.of(), "春季1日到9日种植2株防风草，预算40金币"))
        .isNull();
    assertThat(guard.validate("calculateCropProfit", args(), List.of(), "防风草能赚多少？")).isNotNull();
  }

  @Test
  void preservesClarificationAndRejectsGuessedNumbers() {
    var history = List.of(msg("user", "春季1日到9日种植2株防风草"), msg("assistant", "请问预算是多少？"));
    assertThat(guard.validate("calculateCropProfit", args(), history, "40")).isNull();
    var altered = args();
    altered.put("budget", 99);
    assertThat(guard.validate("calculateCropProfit", altered, history, "40")).isNotNull();
  }

  @Test
  void doesNotCarryOldCropConditionsAcrossAnotherTopic() {
    var history =
        List.of(
            msg("user", "春季1日到9日种植2株防风草，预算40金币"),
            msg("assistant", "完成"),
            msg("user", "海莉喜欢什么礼物？"),
            msg("assistant", "请问送礼对象？"));
    assertThat(guard.validate("calculateCropProfit", args(), history, "种植收益呢？")).isNotNull();
  }

  @Test
  void inventoryIsNotProductionQuantity() {
    assertThat(
            guard.validate(
                "expandMaterialRequirements", Map.of("quantity", 2), List.of(), "已有2个铜锭，制作洒水器"))
        .isNotNull();
  }

  @Test
  void materialQuantityCanBeChinese() {
    assertThat(
            guard.validate(
                "expandMaterialRequirements", Map.of("quantity", 2), List.of(), "制作两个洒水器"))
        .isNull();
  }

  @Test
  void rejectsGuessedGoldAndInventedStock() {
    assertThat(
            guard.validate("getBundleMissing", Map.of("gold", 999), List.of(), "2500金收集包，可用金币2400"))
        .isNotNull();
    assertThat(
            guard.validate(
                "getBundleMissing", Map.of("gold", 2400), List.of(), "2500金收集包，可用金币2400"))
        .isNull();
    assertThat(
            guard.validate(
                "expandMaterialRequirements",
                Map.of("quantity", 2, "owned", Map.of("copper-bar", 99)),
                List.of(),
                "制作两个洒水器"))
        .isNotNull();
  }

  @Test
  void malformedNumericArgumentsBecomeClarification() {
    assertThat(
            guard.validate(
                "expandMaterialRequirements", Map.of("quantity", "two"), List.of(), "制作两个洒水器"))
        .isNotNull();
  }

  @Test
  void anotherToolFormClearsPriorSeasonEvidence() {
    var history =
        List.of(
            msg("user", "春季1日到9日种植2株防风草，预算40金币"),
            msg("assistant", "完成"),
            msg("user", "表单：{\"tool\":\"findFish\",\"args\":{}}"),
            msg("assistant", "查询完成"));
    assertThat(guard.validate("calculateCropProfit", args(), history, "1日到9日种植2株防风草，预算40金币"))
        .isNotNull();
  }
}
