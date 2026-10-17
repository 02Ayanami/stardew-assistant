package dev.stardew.assistant.crop;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.common.*;
import dev.stardew.assistant.data.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CropTools {
  private final GameDataRepository repository;
  private final CropCalculator calculator;
  private final ObjectMapper mapper;

  public CropTools(GameDataRepository r, CropCalculator c, ObjectMapper m) {
    repository = r;
    calculator = c;
    mapper = m;
  }

  public ToolResult<List<CropProjection>> calculateCropProfit(CropScenario s) {
    if (s.quantity() == null || s.budget() == null || s.start() == null || s.end() == null)
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, "DATE_QUANTITY_BUDGET_REQUIRED");
    if (!CropRules.supports(s.rules()))
      return ToolResult.fail(ToolStatus.UNSUPPORTED, CropRules.UNSUPPORTED_MESSAGE);
    try {
      List<GameEntity> entities =
          s.cropIds() == null || s.cropIds().isEmpty()
              ? repository.all("crop")
              : s.cropIds().stream().map(id -> repository.get(id)).toList();
      var results = new ArrayList<CropProjection>();
      var sources = new ArrayList<SourceRef>();
      for (var e : entities) {
        if (!e.type().equals("crop")) throw new IllegalArgumentException("非作物实体");
        var a = e.attributes();
        results.add(
            calculator.calculate(
                s,
                new CropDefinition(
                    e.id(),
                    e.name(),
                    a.path("growthDays").asInt(),
                    a.path("regrowDays").asInt(),
                    mapper.convertValue(
                        a.path("seasons"),
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}),
                    a.path("seedPrice").asLong(),
                    a.path("sellPrice").asLong(),
                    a.path("yieldPerHarvest").asInt())));
        sources.add(e.source());
        if (a.has("seedSource"))
          sources.add(mapper.convertValue(a.get("seedSource"), SourceRef.class));
      }
      return new ToolResult<>(
          ToolStatus.SUCCESS,
          results,
          sources.stream().distinct().toList(),
          List.of(
              "普通品质；每天浇水；固定数量补种；无肥料或职业加成；皮埃尔种子价格；直接出售；不计随机额外产出",
              "未指定年号时，日期用相对第1年标记；可按种植计划调整，不代表读取农场年份"),
          null);
    } catch (UnsupportedOperationException e) {
      return ToolResult.fail(ToolStatus.UNSUPPORTED, e.getMessage());
    } catch (IllegalArgumentException e) {
      return ToolResult.fail(ToolStatus.ERROR, e.getMessage());
    }
  }
}
