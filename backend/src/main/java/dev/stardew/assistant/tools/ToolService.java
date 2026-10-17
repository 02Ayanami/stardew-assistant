package dev.stardew.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.common.*;
import dev.stardew.assistant.crop.*;
import dev.stardew.assistant.data.*;
import dev.stardew.assistant.domain.*;
import dev.stardew.assistant.knowledge.GuideSearchService;
import dev.stardew.assistant.material.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ToolService {
  public static final Set<String> FORM_TOOLS =
      Set.of(
          "calculateCropProfit",
          "expandMaterialRequirements",
          "findFish",
          "queryGiftPreferences",
          "getBundleMissing");
  private final GameDataRepository data;
  private final CropTools crops;
  private final MaterialTools materials;
  private final DomainTools domains;
  private final GuideSearchService guides;
  private final ObjectMapper json;

  public ToolService(
      GameDataRepository data,
      CropTools crops,
      MaterialTools materials,
      DomainTools domains,
      GuideSearchService guides,
      ObjectMapper json) {
    this.data = data;
    this.crops = crops;
    this.materials = materials;
    this.domains = domains;
    this.guides = guides;
    this.json = ExactInputs.mapper(json);
  }

  public ToolResult<?> execute(String name, Map<String, Object> args, Duration timeout) {
    data.requireReady();
    try {
      return switch (name) {
        case "calculateCropProfit" ->
            crops.calculateCropProfit(json.convertValue(args, CropScenario.class));
        case "expandMaterialRequirements" ->
            args.get("targetId") == null || args.get("quantity") == null
                ? ToolResult.fail(ToolStatus.NEEDS_INPUT, "TARGET_QUANTITY_REQUIRED")
                : materials.expandMaterialRequirements(
                    json.convertValue(args, MaterialRequest.class));
        case "findFish" -> domains.findFish(json.convertValue(args, FishRequest.class));
        case "queryGiftPreferences" ->
            domains.queryGiftPreferences(json.convertValue(args, GiftRequest.class));
        case "getBundleMissing" ->
            domains.getBundleMissing(json.convertValue(args, BundleRequest.class));
        case "queryGameData" -> query(args);
        case "listCrops" -> list(args);
        case "searchGuide" ->
            guides.search((String) args.get("text"), (String) args.get("topic"), timeout);
        default -> ToolResult.fail(ToolStatus.ERROR, "UNKNOWN_TOOL");
      };
    } catch (IllegalArgumentException | ClassCastException e) {
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, "INVALID_INPUT: " + e.getMessage());
    }
  }

  private ToolResult<?> query(Map<String, Object> args) {
    if (!(args.get("idOrName") instanceof String q) || q.isBlank())
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, "ENTITY_REQUIRED");
    var hits = data.find(new EntityQuery(String.valueOf(args.getOrDefault("type", "all")), q));
    if (hits.isEmpty()) hits = data.find(new EntityQuery("all", q));
    return new ToolResult<>(
        hits.isEmpty()
            ? ToolStatus.NO_DATA
            : hits.size() > 1 ? ToolStatus.NEEDS_INPUT : ToolStatus.SUCCESS,
        new EntityMatches(hits),
        hits.stream().map(GameEntity::source).filter(Objects::nonNull).distinct().toList(),
        List.of(),
        hits.isEmpty() ? "ENTITY_NOT_FOUND" : hits.size() > 1 ? "AMBIGUOUS_ENTITY" : null);
  }

  private ToolResult<?> list(Map<String, Object> args) {
    String season = (String) args.get("season");
    if (season != null && !GameDate.SEASONS.contains(season))
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, "INVALID_SEASON");
    var all =
        data.all("crop").stream()
            .filter(
                e ->
                    season == null
                        || java.util.stream.StreamSupport.stream(
                                e.attributes().path("seasons").spliterator(), false)
                            .anyMatch(s -> season.equals(s.asText())))
            .toList();
    return ToolResult.ok(
        new CropList(all, all.size(), true),
        all.stream().map(GameEntity::source).distinct().toList());
  }

  public Map<String, Object> options(String tool) {
    if (!FORM_TOOLS.contains(tool)) throw new IllegalArgumentException("未知工具");
    data.requireReady();
    var options = new LinkedHashMap<String, Object>();
    options.put("seasons", GameDate.SEASONS);
    options.put("weather", List.of("sun", "rain", "any"));
    options.put("qualities", List.of("normal", "silver", "gold", "iridium"));
    options.put("levels", List.of("love", "like", "neutral", "dislike", "hate"));
    var items = new TreeMap<String, GameEntity>();
    for (String type : List.of("item", "crop", "fish"))
      for (var e : data.all(type)) items.putIfAbsent(e.id(), e);
    switch (tool) {
      case "calculateCropProfit" -> options.put("crops", data.all("crop"));
      case "expandMaterialRequirements" -> {
        options.put(
            "targets",
            data.all("recipe").stream()
                .filter(
                    e ->
                        e.attributes().path("ingredients").isObject()
                            && !e.attributes().path("ingredients").isEmpty())
                .toList());
        options.put("items", items.values());
      }
      case "queryGiftPreferences" -> {
        options.put("villagers", data.all("villager"));
        options.put("items", items.values());
      }
      case "getBundleMissing" -> {
        options.put("bundles", data.all("bundle"));
        options.put("items", items.values());
      }
      case "findFish" -> {
        var fish = data.all("fish");
        options.put("fish", fish);
        var locations = new TreeSet<String>();
        for (var f : fish)
          for (var habitat : f.attributes().path("habitats"))
            for (var location : habitat.path("locations")) locations.add(location.asText());
        options.put("locations", locations);
      }
    }
    return Map.of("tool", tool, "options", options);
  }
}
