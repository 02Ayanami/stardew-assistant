package dev.stardew.assistant.ai;

import dev.stardew.assistant.common.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Calendar changes must be grounded in the user's reply, not model defaults or assistant examples.
 */
public final class DateGrounding {
  private static final String NUMBER = "[0-9一二两三四五六七八九十廿]+";
  private static final Pattern DAYS =
      Pattern.compile(
          "(?:第\\s*("
              + NUMBER
              + ")\\s*(?:天|日)|(?:春|夏|秋|冬)(?:季|天)?\\s*("
              + NUMBER
              + ")(?:天|日)?|("
              + NUMBER
              + ")\\s*(?:日|号)|(?:到|至|截止)\\s*("
              + NUMBER
              + ")\\s*天)");
  private static final Pattern SEASONS =
      Pattern.compile("春季|春天|夏季|夏天|秋季|秋天|冬季|冬天|[春夏秋冬](?=[第0-9一二三四五六七八九十])");
  private static final Pattern YEARS = Pattern.compile("第\\s*(" + NUMBER + ")\\s*年");

  private DateGrounding() {}

  public static Map<String, Object> filter(Map<String, Object> input, InputEvidence evidence) {
    var result = new LinkedHashMap<String, Object>(input);

    String current = evidence.latest();
    String previous = evidence.previous();
    String question =
        previous.contains("先确认") ? previous.substring(previous.lastIndexOf("先确认")) : previous;
    String active =
        current.matches(".*(截止|结束|种到|直到|到(?:春|夏|秋|冬)).*")
                || (question.matches(".*(截止|结束|种到|种至).*") && !question.matches(".*(起始|开始).*"))
            ? "end"
            : "start";
    var days = new LinkedHashSet<Integer>();
    var matcher = DAYS.matcher(current);
    while (matcher.find())
      for (int g = 1; g <= matcher.groupCount(); g++)
        if (matcher.group(g) != null) {
          Integer value = integer(matcher.group(g));
          if (value != null) days.add(value);
        }
    boolean bare = current.trim().matches(NUMBER + "(?:天|日|号)?[。！!？?]?");
    boolean oneDateQuestion =
        question.matches(".*(起始|开始|截止|结束|第几天|哪一天).*") && !question.matches(".*(预算|数量|多少株|几株).*");
    if (days.isEmpty() && bare && evidence.clarification() && oneDateQuestion) {
      Integer value = integer(current.trim().replaceAll("[天日号。！!？?]", ""));
      if (value != null) days.add(value);
    }
    boolean shortDate =
        current.trim().matches("(?:(?:春|夏|秋|冬)(?:季|天)?(?:第)?|第)?" + NUMBER + "(?:天|日|号)?[。！!？?]?");
    if (days.size() == 1 && shortDate && evidence.clarification())
      result.putIfAbsent(active, Map.of("day", days.iterator().next()));
    var currentYears = new ArrayList<Integer>();
    var years = YEARS.matcher(current);
    while (years.find()) {
      Integer value = integer(years.group(1));
      if (value != null) currentYears.add(value);
    }
    var currentSeasons = seasons(current);
    for (String field : List.of("start", "end")) if (clear(current, field)) result.put(field, null);
    for (String field : List.of("start", "end"))
      if (result.containsKey(field)) {
        if (result.get(field) == null && clear(current, field)) continue;
        var supplied = parts(result.get(field));
        if (supplied.get("year") != null) {
          Long yearNumber = ExactInputs.number(supplied.get("year"));
          if (yearNumber < 1 || yearNumber > Integer.MAX_VALUE)
            throw new IllegalArgumentException("年份需要有效正整数");
        }
        var old = parts(evidence.confirmed().get(field));
        var peer = parts(evidence.confirmed().get(field.equals("start") ? "end" : "start"));
        Long proposed =
            supplied.get("day") instanceof Number ? ExactInputs.number(supplied.get("day")) : null;
        Object day =
            proposed != null
                    && proposed >= Integer.MIN_VALUE
                    && proposed <= Integer.MAX_VALUE
                    && days.contains(proposed.intValue())
                ? proposed.intValue()
                : old.get("day");
        if (shortDate && days.size() == 1 && evidence.clarification() && !field.equals(active))
          day = old.get("day");
        String month = null;
        if (!currentSeasons.isEmpty()) {
          String suppliedSeason =
              supplied.get("season") instanceof String candidate ? candidate : null;
          month =
              currentSeasons.contains(suppliedSeason)
                  ? suppliedSeason
                  : currentSeasons.get(field.equals("start") ? 0 : currentSeasons.size() - 1);
        }
        if (month == null) month = old.get("season") instanceof String s ? s : null;
        if (month == null) month = peer.get("season") instanceof String s ? s : null;
        if (month == null)
          month =
              evidence.userMessages().stream()
                  .map(DateGrounding::season)
                  .filter(Objects::nonNull)
                  .reduce((a, b) -> b)
                  .orElse(null);
        if (day == null || month == null) {
          result.remove(field);
          continue;
        }
        Object year = old.getOrDefault("year", peer.getOrDefault("year", 1));
        if (currentYears.size() == 1) year = currentYears.get(0);
        else if (currentYears.size() > 1
            && supplied.get("year") instanceof Number n
            && currentYears.contains(n.intValue())) year = n.intValue();
        // Year 1 is a relative calculation anchor, not inferred farm progress.
        if (year == null) year = 1;
        result.put(field, Map.of("year", year, "season", month, "day", day));
      }
    return result;
  }

  private static boolean clear(String text, String field) {
    String start = "(?:起始|开始|起点)(?:日期|时间)?";
    String end = "(?:截止|结束|终止)(?:日期|时间)?";
    String labels = field.equals("start") ? start : end;
    String either = "(?:" + start + "|" + end + ")";
    var matcher =
        Pattern.compile(
                "(?:清除|取消|重置|删除)\\s*"
                    + either
                    + "(?:\\s*(?:和|及|与|、)\\s*"
                    + either
                    + ")*|"
                    + labels
                    + "\\s*(?:清除|取消|重置|删除)")
            .matcher(text);
    while (matcher.find()) {
      String before = text.substring(Math.max(0, matcher.start() - 6), matcher.start());
      if (!before.matches(".*(?:不要|不用|不|别|请勿)\\s*$")
          && Pattern.compile(labels).matcher(matcher.group()).find()) return true;
    }
    return false;
  }

  private static Map<String, Object> parts(Object value) {
    if (value instanceof Map<?, ?> m) {
      var copy = new HashMap<String, Object>();
      m.forEach((k, v) -> copy.put(String.valueOf(k), v));
      return copy;
    }
    if (value instanceof GameDate d)
      return Map.of("year", d.year(), "season", d.season(), "day", d.day());
    return Map.of();
  }

  private static List<String> seasons(String text) {
    var matcher = SEASONS.matcher(text);
    var result = new ArrayList<String>();
    while (matcher.find())
      result.add(
          switch (matcher.group().charAt(0)) {
            case '春' -> "spring";
            case '夏' -> "summer";
            case '秋' -> "fall";
            case '冬' -> "winter";
            default -> throw new IllegalStateException("UNKNOWN_SEASON");
          });
    return result;
  }

  private static String season(String text) {
    var values = seasons(text);
    return values.isEmpty() ? null : values.get(values.size() - 1);
  }

  private static Integer integer(String s) {
    try {
      return Integer.valueOf(s);
    } catch (NumberFormatException ignored) {
    }
    if (s.startsWith("廿")) s = "二十" + s.substring(1);
    String digits = "零一二三四五六七八九";
    s = s.replace('两', '二');
    int ten = s.indexOf('十');
    if (ten >= 0) {
      int prefix = ten == 0 ? 1 : digits.indexOf(s.charAt(0));
      int suffix = ten == s.length() - 1 ? 0 : digits.indexOf(s.charAt(ten + 1));
      return prefix >= 0 && suffix >= 0 ? prefix * 10 + suffix : null;
    }
    return s.length() == 1 && digits.indexOf(s.charAt(0)) >= 0 ? digits.indexOf(s.charAt(0)) : null;
  }
}
