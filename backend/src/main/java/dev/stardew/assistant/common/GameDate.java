package dev.stardew.assistant.common;

import java.util.List;

public record GameDate(int year, String season, int day) implements Comparable<GameDate> {
  public static final List<String> SEASONS = List.of("spring", "summer", "fall", "winter");

  public GameDate {
    if (year < 1 || !SEASONS.contains(season) || day < 1 || day > 28)
      throw new IllegalArgumentException("非法游戏日期");
  }

  public long absoluteDay() {
    return Math.addExact(
        Math.multiplyExact((long) year - 1, 112), SEASONS.indexOf(season) * 28L + day - 1);
  }

  public GameDate plusDays(int days) {
    long a = Math.addExact(absoluteDay(), days);
    if (a < 0) throw new IllegalArgumentException("日期早于第一年");
    return new GameDate(
        Math.toIntExact(a / 112 + 1), SEASONS.get((int) (a % 112 / 28)), (int) (a % 28) + 1);
  }

  public int compareTo(GameDate other) {
    return Long.compare(absoluteDay(), other.absoluteDay());
  }
}
