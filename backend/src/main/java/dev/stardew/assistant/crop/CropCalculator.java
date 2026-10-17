package dev.stardew.assistant.crop;

import dev.stardew.assistant.common.GameDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CropCalculator {
  public CropProjection calculate(CropScenario s, CropDefinition c) {
    if (s.quantity() == null
        || s.budget() == null
        || s.quantity() < 1
        || s.budget() < 0
        || s.start() == null
        || s.end() == null
        || s.end().compareTo(s.start()) < 0) throw new IllegalArgumentException("数量、预算或日期非法");
    if (!CropRules.supports(s.rules()))
      throw new UnsupportedOperationException(CropRules.UNSUPPORTED_MESSAGE);
    if (c.growthDays() < 1
        || c.regrowDays() < 0
        || c.seedPrice() < 0
        || c.sellPrice() < 0
        || c.yieldPerHarvest() < 1) throw new IllegalArgumentException("作物规则损坏");
    long seed;
    long units;
    long perHarvest;
    try {
      seed = Math.multiplyExact(s.quantity(), c.seedPrice());
      units = Math.multiplyExact(s.quantity(), c.yieldPerHarvest());
      perHarvest = Math.multiplyExact(units, c.sellPrice());
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("数量过大", e);
    }
    if (s.end().absoluteDay() - s.start().absoluteDay() > 1120)
      throw new IllegalArgumentException("单次计算最长十年");
    List<HarvestEvent> events = new ArrayList<>();
    long cost = 0, revenue = 0, cash = s.budget();
    GameDate planted = s.start();
    if (!c.seasons().contains(planted.season())) return result(c, events, 0, 0, false, "起始季节不可种植");
    if (cash < seed) return result(c, events, 0, 0, false, "初始预算不足");
    GameDate harvest = planted.plusDays(c.growthDays());
    if (!survives(planted, harvest, c) || harvest.compareTo(s.end()) > 0)
      return result(c, events, 0, 0, false, "期限内无法收获");
    try {
      cost = seed;
      cash -= seed;
      while (harvest.compareTo(s.end()) <= 0 && survives(planted, harvest, c)) {
        events.add(new HarvestEvent(harvest, units, perHarvest));
        revenue = Math.addExact(revenue, perHarvest);
        cash = Math.addExact(cash, perHarvest);
        GameDate next = harvest.plusDays(c.regrowDays() > 0 ? c.regrowDays() : c.growthDays());
        if (next.compareTo(s.end()) > 0 || !survives(harvest, next, c)) break;
        if (c.regrowDays() == 0) {
          if (cash < seed) return result(c, events, cost, revenue, false, "收获后补种资金不足");
          cash -= seed;
          cost = Math.addExact(cost, seed);
          planted = harvest;
        }
        harvest = next;
      }
      return result(c, events, cost, revenue, true, null);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("计算溢出", e);
    }
  }

  private boolean survives(GameDate start, GameDate end, CropDefinition crop) {
    for (long d = start.absoluteDay(); d <= end.absoluteDay(); d++) {
      var date = start.plusDays(Math.toIntExact(d - start.absoluteDay()));
      if (!crop.seasons().contains(date.season())) return false;
    }
    return true;
  }

  private CropProjection result(
      CropDefinition c,
      List<HarvestEvent> h,
      long cost,
      long revenue,
      boolean feasible,
      String reason) {
    return new CropProjection(
        c.id(),
        c.name(),
        List.copyOf(h),
        cost,
        revenue,
        Math.subtractExact(revenue, cost),
        feasible,
        reason);
  }
}
