package dev.stardew.assistant.ai;

import dev.stardew.assistant.common.ExactInputs;
import java.util.*;
import java.util.regex.Pattern;

/** Model numeric changes require a matching quantity/budget statement in the latest human reply. */
public final class NumericGrounding {
  private static final String NUMBER = "[0-9]+|[零一二两三四五六七八九十百千万]+";

  private NumericGrounding() {}

  public static Map<String, Object> filter(Map<String, Object> proposed, InputEvidence evidence) {
    var result = new LinkedHashMap<>(proposed);
    String text = evidence.latest();
    String previous = evidence.previous();
    String question = previous.replaceAll("(?s)^.*[，,。;；\\n]", "");
    boolean inventoryQuestion = question.matches(".*(?:已有|拥有|库存|背包|持有|已提交|铜锭|铁锭|金锭|材料).*?");
    for (String field : List.of("quantity", "budget")) {
      if (!result.containsKey(field)) continue;
      Object value = result.get(field);
      // Preserve validation failures instead of quietly converting malformed model input.
      if (value != null) {
        try {
          long n = ExactInputs.number(value);
          if (n < 0 || field.equals("quantity") && n == 0) continue;
        } catch (IllegalArgumentException invalid) {
          continue;
        }
      }
      if (clear(text, field)) {
        result.put(field, null);
        continue;
      }
      Long requested = requested(text, field);
      if (field.equals("quantity") && inventoryQuestion && !text.matches(".*(?:数量|制作|合成|种植|种).*"))
        requested = null;
      boolean bare = text.trim().matches("(?:" + NUMBER + ")[。！!？?]?");
      boolean matchingQuestion =
          field.equals("quantity")
              ? question.matches(".*(数量|多少株|几株|几个|多少个).*") && !question.contains("预算")
              : question.contains("预算") && !question.matches(".*(数量|多少株|几株|几个|多少个).*");
      if (requested == null
          && bare
          && evidence.clarification()
          && matchingQuestion
          && !inventoryQuestion) requested = parse(text.trim().replaceAll("[。！!？?]", ""));
      if (requested != null) result.put(field, requested);
      else result.remove(field);
    }
    return result;
  }

  static Long requested(String text, String field) {
    if (field.equals("quantity")) {
      String targetPattern =
          "(?:数量\\s*(?:改为|改成|调整为|设为|为|是|=|:|：)?\\s*("
              + NUMBER
              + ")(?![0-9.eE])|(?:种植|种|制作|合成|做)\\s*("
              + NUMBER
              + ")\\s*(?:株|格|个|份|件))";
      var targets = Pattern.compile(targetPattern).matcher(text);
      var requested = new LinkedHashSet<Long>();
      while (targets.find()) {
        String before = text.substring(Math.max(0, targets.start() - 6), targets.start());
        if (before.matches(".*(?:不是|不要|不用|不|别|取消|清除)\\s*$")) continue;
        Long n = parse(targets.group(1) == null ? targets.group(2) : targets.group(1));
        if (n != null) requested.add(n);
      }
      if (!requested.isEmpty()) return requested.size() == 1 ? requested.iterator().next() : null;
    }
    String pattern =
        field.equals("quantity")
            ? "(?:数量)\\s*(?:改为|改成|调整为|设为|为|是|=|:|：)?\\s*("
                + NUMBER
                + ")(?![0-9.eE])|(?<![-+0-9.])("
                + NUMBER
                + ")\\s*(?:株|格|个|份|件)"
            : "(?:预算|budget|可用金币|金币)\\s*(?:改为|改成|调整为|设为|为|是|有|=|:|：)?\\s*("
                + NUMBER
                + ")(?![0-9.eE])|(?<![-+0-9.])("
                + NUMBER
                + ")\\s*(?:金币|[gG])(?![a-z])";
    var matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text);
    var values = new LinkedHashSet<Long>();
    while (matcher.find()) {
      String before = text.substring(Math.max(0, matcher.start() - 6), matcher.start());
      if (before.matches(".*(?:不是|不要|不用|不|别|取消|清除)\\s*$")) continue;
      if (field.equals("quantity")) {
        String clause = text.substring(0, matcher.start()).replaceAll("(?s)^.*[，,。;；\\n]", "");
        if (clause.matches(".*(?:已有|拥有|库存|背包|持有|已提交|提交|已交).*")) continue;
      }
      Long number = parse(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
      if (number != null) values.add(number);
    }
    return values.size() == 1 ? values.iterator().next() : null;
  }

  private static boolean clear(String text, String field) {
    var matcher =
        Pattern.compile("(?:清除|取消|重置)\\s*" + (field.equals("quantity") ? "(?:种植)?数量" : "预算"))
            .matcher(text);
    while (matcher.find())
      if (!text.substring(Math.max(0, matcher.start() - 6), matcher.start())
          .matches(".*(?:不要|不用|不|别)\\s*$")) return true;
    return false;
  }

  private static Long parse(String text) {
    try {
      return Long.valueOf(text);
    } catch (NumberFormatException ignored) {
    }
    long total = 0, section = 0, digit = 0;
    String digits = "零一二三四五六七八九";
    for (char ch : text.replace('两', '二').toCharArray()) {
      int d = digits.indexOf(ch);
      if (d >= 0) {
        digit = d;
        continue;
      }
      int unit =
          switch (ch) {
            case '十' -> 10;
            case '百' -> 100;
            case '千' -> 1000;
            case '万' -> 10000;
            default -> 0;
          };
      if (unit == 0) return null;
      if (unit == 10000) {
        section += digit;
        total += (section == 0 ? 1 : section) * unit;
        section = 0;
        digit = 0;
      } else {
        section += (digit == 0 ? 1 : digit) * unit;
        digit = 0;
      }
    }
    return total + section + digit;
  }
}
