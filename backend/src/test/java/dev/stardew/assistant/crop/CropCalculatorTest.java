package dev.stardew.assistant.crop;

import static org.junit.jupiter.api.Assertions.*;

import dev.stardew.assistant.common.GameDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class CropCalculatorTest {
  CropDefinition crop(int growth, int regrow, List<String> seasons) {
    return new CropDefinition("test", "合成作物", growth, regrow, seasons, 10, 30, 1);
  }

  CropScenario scenario(GameDate start, GameDate end, long quantity, long budget) {
    return new CropScenario(List.of("test"), start, end, quantity, budget, Map.of());
  }

  @Test
  void harvestAndReplantWithinDeadline() {
    var r =
        new CropCalculator()
            .calculate(
                scenario(new GameDate(1, "spring", 1), new GameDate(1, "spring", 9), 2, 20),
                crop(4, 0, List.of("spring")));
    assertEquals(List.of(5, 9), r.harvests().stream().map(h -> h.date().day()).toList());
    assertEquals(40, r.totalCost());
    assertEquals(120, r.totalRevenue());
    assertEquals(80, r.netProfit());
  }

  @Test
  void acceptsExplicitValuesForSupportedBaselineRules() {
    var s =
        new CropScenario(
            List.of("test"),
            new GameDate(1, "spring", 1),
            new GameDate(1, "spring", 9),
            2L,
            20L,
            Map.of(
                "quality", "normal",
                "fertilizer", "none",
                "professions", "none",
                "replant", "fixed_quantity",
                "sell", "direct"));
    var r = new CropCalculator().calculate(s, crop(4, 0, List.of("spring")));
    assertEquals(List.of(5, 9), r.harvests().stream().map(h -> h.date().day()).toList());
    assertEquals(40, r.totalCost());
    assertEquals(120, r.totalRevenue());
    assertEquals(80, r.netProfit());
  }

  @Test
  void regrowDoesNotBuySeedsAgain() {
    var r =
        new CropCalculator()
            .calculate(
                scenario(new GameDate(1, "spring", 1), new GameDate(1, "spring", 9), 2, 20),
                crop(4, 3, List.of("spring")));
    assertEquals(20, r.totalCost());
    assertEquals(120, r.totalRevenue());
  }

  @Test
  void rejectsInsufficientInitialCash() {
    assertFalse(
        new CropCalculator()
            .calculate(
                scenario(new GameDate(1, "spring", 1), new GameDate(1, "spring", 9), 2, 19),
                crop(4, 0, List.of("spring")))
            .feasible());
  }

  @Test
  void seasonEndKillsSingleSeasonCrop() {
    var r =
        new CropCalculator()
            .calculate(
                scenario(new GameDate(1, "spring", 26), new GameDate(1, "summer", 10), 1, 10),
                crop(4, 0, List.of("spring")));
    assertTrue(r.harvests().isEmpty());
    assertFalse(r.feasible());
  }

  @Test
  void multiSeasonCropSurvivesBoundary() {
    var r =
        new CropCalculator()
            .calculate(
                scenario(new GameDate(1, "summer", 26), new GameDate(1, "fall", 4), 1, 10),
                crop(4, 0, List.of("summer", "fall")));
    assertEquals(new GameDate(1, "fall", 2), r.harvests().get(0).date());
  }

  @Test
  void rejectsInvalidOrOverflowQuantity() {
    for (long q : new long[] {0, -1, Long.MAX_VALUE})
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new CropCalculator()
                  .calculate(
                      scenario(new GameDate(1, "spring", 1), new GameDate(1, "spring", 9), q, 20),
                      crop(4, 0, List.of("spring"))));
  }

  @Test
  void rejectsUnsupportedRules() {
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            new CropCalculator()
                .calculate(
                    new CropScenario(
                        List.of("test"),
                        new GameDate(1, "spring", 1),
                        new GameDate(1, "spring", 9),
                        2L,
                        20L,
                        Map.of("fertilizer", true)),
                    crop(4, 0, List.of("spring"))));
  }

  @Test
  void rejectsReversedDates() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CropCalculator()
                .calculate(
                    scenario(new GameDate(1, "spring", 9), new GameDate(1, "spring", 1), 2, 20),
                    crop(4, 0, List.of("spring"))));
  }
}
