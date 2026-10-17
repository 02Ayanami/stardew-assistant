package dev.stardew.assistant.material;

import dev.stardew.assistant.common.*;
import dev.stardew.assistant.data.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MaterialTools {
  private final GameDataRepository repository;
  private final MaterialExpander expander;

  public MaterialTools(GameDataRepository r, MaterialExpander e) {
    repository = r;
    expander = e;
  }

  public ToolResult<MaterialExpansion> expandMaterialRequirements(MaterialRequest request) {
    try {
      var catalog = new HashMap<String, GameEntity>();
      for (String type : List.of("recipe", "item"))
        repository.all(type).forEach(e -> catalog.put(e.id(), e));
      var owned = new LinkedHashMap<String, Long>();
      if (request.owned() != null)
        for (var entry : request.owned().entrySet()) {
          String key = entry.getKey();
          if (key == null || entry.getValue() == null || entry.getValue() < 0)
            throw new IllegalArgumentException("库存非法");
          String canonical = key.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
          if (!catalog.containsKey(canonical)) {
            var matches =
                repository.find(new EntityQuery("all", key)).stream()
                    .filter(e -> catalog.containsKey(e.id()))
                    .toList();
            if (matches.size() != 1)
              return ToolResult.fail(
                  ToolStatus.NEEDS_INPUT, "OWNED_ENTITY_UNKNOWN_OR_AMBIGUOUS: " + key);
            canonical = matches.get(0).id();
          }
          if (owned.putIfAbsent(canonical, entry.getValue()) != null)
            return ToolResult.fail(ToolStatus.NEEDS_INPUT, "DUPLICATE_OWNED_ENTITY: " + canonical);
        }
      var result =
          expander.expand(
              new MaterialRequest(request.targetId(), request.quantity(), owned), catalog);
      return new ToolResult<>(
          result.gaps().isEmpty() ? ToolStatus.SUCCESS : ToolStatus.NO_DATA,
          result,
          result.sources(),
          List.of("按收录路线展开；前置设施重复使用，不作为每件产品的耗材"),
          result.gaps().isEmpty() ? null : "MATERIAL_DATA_GAP");
    } catch (IllegalArgumentException e) {
      return ToolResult.fail(ToolStatus.ERROR, e.getMessage());
    }
  }
}
