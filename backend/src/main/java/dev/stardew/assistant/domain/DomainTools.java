package dev.stardew.assistant.domain;

import com.fasterxml.jackson.databind.JsonNode;
import dev.stardew.assistant.common.*;
import dev.stardew.assistant.data.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Deterministic rules over the current dataset's curated entities. */
@Service
public class DomainTools {
  private final GameDataRepository repository;
  private static final List<String> LEVELS = List.of("love", "like", "neutral", "dislike", "hate");
  private static final List<String> QUALITIES = List.of("normal", "silver", "gold", "iridium");
  private static final Set<String> SEASONS = Set.of("spring", "summer", "fall", "winter");

  public DomainTools(GameDataRepository repository) {
    this.repository = repository;
  }

  public ToolResult<FishMatches> findFish(FishRequest r) {
    try {

      String version = supportedVersion();
      String season = normalized(r.season()),
          weather = normalized(r.weather()),
          location = normalized(r.location());
      if (season != null && !SEASONS.contains(season)) throw new Input("UNKNOWN_SEASON");
      if (weather != null && !Set.of("sun", "rain", "any").contains(weather))
        throw new Input("UNKNOWN_WEATHER");
      if (r.time() != null && !validTime(r.time())) throw new Input("INVALID_TIME_0600_2600");
      var catalog = repository.all("fish");
      var candidates = select(catalog, r.fishName(), "FISH");
      var locations = new HashSet<String>();
      for (var e : catalog) for (var h : habitats(e)) locations.addAll(h.locations());
      if (location != null && !locations.contains(location)) throw new Input("UNKNOWN_LOCATION");
      var hits = new ArrayList<FishMatch>();
      var sources = new LinkedHashSet<SourceRef>();
      for (var e : candidates) {
        var matching =
            habitats(e).stream()
                .filter(
                    h ->
                        (season == null || h.seasons().contains(season))
                            && (weather == null
                                || weather.equals("any")
                                || h.weather().contains("any")
                                || h.weather().contains(weather))
                            && (location == null || h.locations().contains(location))
                            && (r.time() == null
                                || r.time() >= h.startTime() && r.time() <= h.endTime()))
                .toList();
        if (!matching.isEmpty()) {
          hits.add(new FishMatch(e.id(), e.name(), matching));
          source(sources, e, version);
        }
      }
      return result(new FishMatches(List.copyOf(hits)), sources, hits.isEmpty());
    } catch (Input e) {
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, e.getMessage());
    } catch (Rules e) {
      return ToolResult.fail(ToolStatus.UNSUPPORTED, e.getMessage());
    }
  }

  public ToolResult<GiftMatches> queryGiftPreferences(GiftRequest r) {
    try {

      String version = supportedVersion();
      String level = normalized(r.level());
      if (level != null && !LEVELS.contains(level)) throw new Input("UNKNOWN_GIFT_LEVEL");
      if (normalized(r.villager()) == null && normalized(r.item()) == null)
        throw new Input("VILLAGER_OR_ITEM_REQUIRED");
      var villagers = select(repository.all("villager"), r.villager(), "VILLAGER");
      var items = select(items(), r.item(), "ITEM");
      var globals = repository.all("gift-rules");
      if (globals.size() != 1) throw new Rules("UNIVERSAL_GIFT_RULES_MISSING_OR_AMBIGUOUS");
      var global = globals.get(0);
      var hits = new ArrayList<GiftPreference>();
      var sources = new LinkedHashSet<SourceRef>();
      for (var villager : villagers)
        for (var item : items) {
          var prefs = villager.attributes().path("giftPreferences");
          var categories = optionalStrings(item.attributes().path("giftCategories"));
          String found = preference(prefs.path("personal"), List.of(item.id()));
          String rule = "personal-item";
          if (found == null) {
            found = preference(prefs.path("personalCategories"), categories);
            rule = "personal-category";
          }
          if (found == null) {
            found = preference(global.attributes().path("universal"), List.of(item.id()));
            rule = "universal-item";
          }
          if (found == null) {
            found = preference(global.attributes().path("universalCategories"), categories);
            rule = "universal-category";
          }
          if (found != null && (level == null || level.equals(found))) {
            hits.add(
                new GiftPreference(
                    villager.id(), villager.name(), item.id(), item.name(), found, rule));
            source(sources, villager, version);
            source(sources, item, version);
            source(sources, global, version);
          }
        }
      return result(new GiftMatches(List.copyOf(hits)), sources, hits.isEmpty());
    } catch (Input e) {
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, e.getMessage());
    } catch (Rules e) {
      return ToolResult.fail(ToolStatus.UNSUPPORTED, e.getMessage());
    }
  }

  public ToolResult<BundleMissing> getBundleMissing(BundleRequest r) {
    try {

      String version = supportedVersion();
      if (normalized(r.bundle()) == null) throw new Input("BUNDLE_REQUIRED");
      var bundle = select(repository.all("bundle"), r.bundle(), "BUNDLE").get(0);
      var a = bundle.attributes();
      if (!"normal".equals(text(a, "mode"))) throw new Rules("ONLY_NORMAL_BUNDLES_SUPPORTED");
      long gold = integer(a, "gold", 0, Long.MAX_VALUE);
      int slots = (int) integer(a, "requiredSlots", 0, Integer.MAX_VALUE);
      var requirements = a.path("requirements");
      if (!requirements.isArray() || slots > requirements.size())
        throw new Rules("INVALID_BUNDLE_REQUIREMENTS");
      if (gold > 0 && (!requirements.isEmpty() || slots != 0))
        throw new Rules("MIXED_GOLD_ITEM_BUNDLE_UNSUPPORTED");
      if (r.gold() != null && r.gold() < 0) throw new Input("INVALID_GOLD");
      if (gold > 0 && r.gold() == null) throw new Input("GOLD_REQUIRED");
      if (gold > 0) {
        if (r.submitted() != null && !r.submitted().isEmpty())
          throw new Input("GOLD_BUNDLE_DOES_NOT_ACCEPT_ITEMS");
        var sources = new LinkedHashSet<SourceRef>();
        source(sources, bundle, version);
        return result(
            new BundleMissing(
                bundle.id(), bundle.name(), 0, 0, 0, List.of(), gold, Math.max(0, gold - r.gold())),
            sources,
            false);
      }
      if (r.submitted() == null || r.owned() == null)
        throw new Input("BUNDLE_SUBMITTED_AND_OWNED_REQUIRED");
      var catalog = items();
      var required = new ArrayList<Requirement>();
      var slotIds = new HashSet<String>();
      for (var entry : requirements) {
        String id = text(entry, "itemId"), quality = text(entry, "quality");
        if (!QUALITIES.contains(quality)) throw new Rules("INVALID_REQUIRED_QUALITY");
        var entity = select(catalog, id, "BUNDLE_ITEM").get(0);
        if (!entity.id().equals(id)) throw new Rules("BUNDLE_ITEM_MUST_USE_CANONICAL_ID");
        String slotId =
            entry.has("slotId") ? text(entry, "slotId") : Integer.toString(required.size());
        if (!slotIds.add(slotId)) throw new Rules("DUPLICATE_BUNDLE_SLOT_ID");
        required.add(
            new Requirement(
                entity, integer(entry, "quantity", 1, Long.MAX_VALUE), quality, slotId));
      }
      var submitted = normalizeInventory(r.submitted(), catalog);
      var owned = normalizeInventory(r.owned(), catalog);
      for (var item : submitted) {
        boolean eligible =
            required.stream()
                .anyMatch(
                    req ->
                        req.entity().id().equals(item.itemId())
                            && QUALITIES.indexOf(item.quality())
                                >= QUALITIES.indexOf(req.quality()));
        if (!eligible) throw new Input("INVALID_SUBMITTED_ITEM_OR_QUALITY");
      }
      // Allocate once across slots, highest minimum quality first. Within a requirement,
      // use the lowest qualifying stack to preserve higher quality inventory.
      var order = new ArrayList<Integer>();
      for (int i = 0; i < required.size(); i++) order.add(i);
      order.sort(
          Comparator.comparingInt((Integer i) -> QUALITIES.indexOf(required.get(i).quality()))
              .reversed());
      var submittedPool = pool(submitted);
      var ownedPool = pool(owned);
      long[] allocatedSubmitted = new long[required.size()],
          allocatedOwned = new long[required.size()];
      for (int i : order)
        allocatedSubmitted[i] = consume(submittedPool, required.get(i), required.get(i).quantity());
      for (int i : order)
        allocatedOwned[i] =
            consume(ownedPool, required.get(i), required.get(i).quantity() - allocatedSubmitted[i]);
      int completed = 0;
      var gaps = new ArrayList<BundleGap>();
      var sources = new LinkedHashSet<SourceRef>();
      source(sources, bundle, version);
      for (int i = 0; i < required.size(); i++) {
        var req = required.get(i);
        long haveSubmitted = allocatedSubmitted[i], usable = allocatedOwned[i];
        long needed = req.quantity() - haveSubmitted;
        if (needed == 0) completed++;
        gaps.add(
            new BundleGap(
                req.entity().id(),
                req.entity().name(),
                req.quantity(),
                req.quality(),
                haveSubmitted,
                usable,
                needed - usable,
                req.slotId()));
        source(sources, req.entity(), version);
      }
      if (completed > slots) throw new Input("TOO_MANY_SUBMITTED_CHOICES");
      return result(
          new BundleMissing(
              bundle.id(),
              bundle.name(),
              slots,
              completed,
              slots - completed,
              List.copyOf(gaps),
              gold,
              Math.max(0, gold - (r.gold() == null ? 0 : r.gold()))),
          sources,
          false);
    } catch (Input e) {
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, e.getMessage());
    } catch (Rules e) {
      return ToolResult.fail(ToolStatus.UNSUPPORTED, e.getMessage());
    } catch (ArithmeticException e) {
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, "QUANTITY_OVERFLOW");
    }
  }

  private List<GameEntity> items() {
    var all = new LinkedHashMap<String, GameEntity>();
    for (var type : List.of("item", "crop", "fish"))
      for (var e : repository.all(type)) all.putIfAbsent(e.id(), e);
    return List.copyOf(all.values());
  }

  private List<GameEntity> select(List<GameEntity> entities, String query, String kind) {
    if (query == null) return entities;
    String q = normalized(query);
    if (q == null) throw new Input("EMPTY_" + kind);
    var matches =
        entities.stream()
            .filter(
                e ->
                    q.equals(normalized(e.id()))
                        || q.equals(normalized(e.name()))
                        || e.aliases() != null
                            && e.aliases().stream().anyMatch(alias -> q.equals(normalized(alias))))
            .toList();
    if (matches.size() != 1) throw new Input(kind + "_UNKNOWN_OR_AMBIGUOUS");
    return matches;
  }

  private List<FishHabitat> habitats(GameEntity e) {
    var nodes = e.attributes().path("habitats");
    if (!nodes.isArray() || nodes.isEmpty()) throw new Rules("FISH_HABITATS_MISSING: " + e.id());
    var result = new ArrayList<FishHabitat>();
    for (var node : nodes) {
      var seasons = strings(node.path("seasons"));
      var weather = strings(node.path("weather"));
      var locations = strings(node.path("locations"));
      int start = (int) integer(node, "startTime", 600, 2600),
          end = (int) integer(node, "endTime", 600, 2600);
      if (!SEASONS.containsAll(seasons)
          || !Set.of("sun", "rain", "any").containsAll(weather)
          || !validTime(start)
          || !validTime(end)
          || start > end) throw new Rules("INVALID_FISH_HABITAT: " + e.id());
      result.add(new FishHabitat(seasons, weather, locations, start, end));
    }
    return List.copyOf(result);
  }

  private String preference(JsonNode rules, List<String> keys) {
    if (rules.isMissingNode() || rules.isNull()) return null;
    if (!rules.isObject()) throw new Rules("INVALID_GIFT_RULES");
    String found = null;
    for (var level : LEVELS)
      for (var id : optionalStrings(rules.path(level)))
        if (keys.contains(id)) {
          if (found != null && !found.equals(level)) throw new Rules("CONFLICTING_GIFT_RULES");
          found = level;
        }
    return found;
  }

  private List<BundleItem> normalizeInventory(
      List<BundleItem> inventory, List<GameEntity> catalog) {
    var result = new ArrayList<BundleItem>();
    var keys = new HashSet<String>();
    for (var entry : inventory) {
      if (entry == null
          || entry.quantity() == null
          || entry.quantity() < 0
          || normalized(entry.itemId()) == null
          || entry.quality() == null
          || !QUALITIES.contains(entry.quality()))
        throw new Input("INVALID_INVENTORY_ITEM_QUANTITY_OR_QUALITY");
      var entity = select(catalog, entry.itemId(), "INVENTORY_ITEM").get(0);
      if (!keys.add(entity.id() + ":" + entry.quality()))
        throw new Input("DUPLICATE_INVENTORY_ITEM_QUALITY");
      result.add(new BundleItem(entity.id(), entry.quantity(), entry.quality()));
    }
    return result;
  }

  private Map<String, long[]> pool(List<BundleItem> inventory) {
    var result = new HashMap<String, long[]>();
    for (var item : inventory) {
      var amounts = result.computeIfAbsent(item.itemId(), key -> new long[QUALITIES.size()]);
      int quality = QUALITIES.indexOf(item.quality());
      amounts[quality] = Math.addExact(amounts[quality], item.quantity());
    }
    return result;
  }

  private long consume(Map<String, long[]> pool, Requirement req, long needed) {
    var amounts = pool.get(req.entity().id());
    if (amounts == null) return 0;
    long remaining = needed;
    for (int q = QUALITIES.indexOf(req.quality()); q < QUALITIES.size() && remaining > 0; q++) {
      long use = Math.min(remaining, amounts[q]);
      amounts[q] -= use;
      remaining -= use;
    }
    return needed - remaining;
  }

  private record Requirement(GameEntity entity, long quantity, String quality, String slotId) {}

  private static boolean validTime(int time) {
    return time >= 600 && time <= 2600 && time % 100 < 60;
  }

  private static String normalized(String s) {
    return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
  }

  private static String text(JsonNode node, String key) {
    var value = node.path(key);
    if (!value.isTextual() || value.asText().isBlank())
      throw new Rules("INVALID_RULE_FIELD: " + key);
    return value.asText();
  }

  private static long integer(JsonNode node, String key, long min, long max) {
    var value = node.path(key);
    if (!value.isIntegralNumber()
        || !value.canConvertToLong()
        || value.longValue() < min
        || value.longValue() > max) throw new Rules("INVALID_RULE_FIELD: " + key);
    return value.longValue();
  }

  private static List<String> strings(JsonNode node) {
    var list = optionalStrings(node);
    if (list.isEmpty()) throw new Rules("MISSING_CONDITION_VALUES");
    return list;
  }

  private static List<String> optionalStrings(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) return List.of();
    if (!node.isArray()) throw new Rules("INVALID_RULE_ARRAY");
    var values = new ArrayList<String>();
    for (var value : node) {
      if (!value.isTextual() || value.asText().isBlank())
        throw new Rules("INVALID_RULE_ARRAY_VALUE");
      values.add(value.asText());
    }
    return List.copyOf(values);
  }

  private String supportedVersion() {
    try {
      String version = repository.gameVersion();
      if (version == null
          || !version.split("[（(]", 2)[0].trim().matches("1\\.6(?:\\.(?:[0-9]+|x))?"))
        throw new IllegalArgumentException("unsupported snapshot version");
      return version;
    } catch (IllegalArgumentException failure) {
      throw new Rules("DOMAIN_VERSION_UNSUPPORTED");
    }
  }

  private static void source(Set<SourceRef> sources, GameEntity e, String snapshotVersion) {
    var source = e.source();
    try {
      if (source == null || source.gameVersion() == null)
        throw new IllegalArgumentException("missing provenance");
      String version = source.gameVersion().split("[（(]", 2)[0].trim();
      if (!version.matches("1\\.6(?:\\.(?:[0-9]+|x))?"))
        throw new IllegalArgumentException("unsupported version");
      SourceValidation.validate(source, source.snapshotId(), snapshotVersion);
      if ("PLAYER_GUIDE".equals(source.sourceType()))
        throw new IllegalArgumentException("advice is not deterministic fact");
      sources.add(source);
    } catch (IllegalArgumentException failure) {
      throw new Rules("DOMAIN_SOURCE_INVALID: " + e.id());
    }
  }

  private static <T> ToolResult<T> result(T data, Set<SourceRef> sources, boolean empty) {
    return new ToolResult<>(
        empty ? ToolStatus.NO_DATA : ToolStatus.SUCCESS,
        data,
        List.copyOf(sources),
        List.of("仅覆盖当前资料收录的普通 PC 1.6 规则；献祭背包仅核算当前单个收集包，不分配给其他收集包"),
        empty ? "NO_CURATED_MATCH" : null);
  }

  private static class Input extends RuntimeException {
    Input(String code) {
      super(code);
    }
  }

  private static class Rules extends RuntimeException {
    Rules(String code) {
      super(code);
    }
  }
}
