package dev.stardew.assistant.crop;

import java.util.Locale;
import java.util.Map;

/** Explicit values that restate the deterministic crop calculator's supported baseline. */
public final class CropRules {
  public static final String UNSUPPORTED_MESSAGE = "仅支持普通品质、无肥料、无职业加成、固定数量补种和直接出售";

  private static final Map<String, String> SUPPORTED_BASE_VALUES =
      Map.of(
          "quality", "normal",
          "fertilizer", "none",
          "professions", "none",
          "replant", "fixed_quantity",
          "sell", "direct");

  private CropRules() {}

  public static boolean supports(Map<?, ?> rules) {
    if (rules == null || rules.isEmpty()) return true;
    for (var entry : rules.entrySet()) {
      if (!(entry.getKey() instanceof String rawKey)) return false;
      String key = normalize(rawKey);
      String supportedValue = SUPPORTED_BASE_VALUES.get(key);
      if (supportedValue == null) return false;
      Object value = entry.getValue();
      if (value == null) return false;
      if (!(value instanceof String rawValue) || !supportedValue.equals(normalize(rawValue)))
        return false;
    }
    return true;
  }

  /** Natural-language conditions must contain only explicitly recognized baseline phrases. */
  public static boolean supportsConfirmed(Object rules) {
    if (rules == null) return true;
    if (rules instanceof Map<?, ?> map) return supports(map);
    if (!(rules instanceof String text) || text.isBlank()) return false;
    String remaining = text.toLowerCase(Locale.ROOT).trim();
    for (String phrase :
        java.util.List.of(
            "normal quality",
            "direct sell",
            "fixed quantity replant",
            "no fertilizer",
            "no profession bonuses",
            "no professions",
            "普通品质",
            "直接出售",
            "固定数量补种",
            "无肥料",
            "无职业加成")) {
      String boundary = phrase.charAt(0) < 128 ? "(?<![a-z0-9_-])" : "";
      String end = phrase.charAt(0) < 128 ? "(?![a-z0-9_-])" : "";
      remaining = remaining.replaceAll(boundary + java.util.regex.Pattern.quote(phrase) + end, " ");
    }
    return remaining.replaceAll("[\\s,，;；、.。]+", "").isEmpty();
  }

  private static String normalize(String value) {
    return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
  }
}
