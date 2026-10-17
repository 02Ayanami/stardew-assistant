package dev.stardew.assistant.material;

import dev.stardew.assistant.common.SourceRef;
import dev.stardew.assistant.data.GameEntity;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MaterialExpander {
  public MaterialExpansion expand(MaterialRequest request, Map<String, GameEntity> catalog) {
    if (request.quantity() < 1 || request.targetId() == null)
      throw new IllegalArgumentException("目标或数量非法");
    var owned = new HashMap<String, Long>(request.owned() == null ? Map.of() : request.owned());
    if (owned.values().stream().anyMatch(n -> n == null || n < 0))
      throw new IllegalArgumentException("已有材料数量非法");
    var totals = new TreeMap<String, Long>();
    var missing = new TreeMap<String, Long>();
    var gaps = new LinkedHashSet<String>();
    var pre = new LinkedHashSet<String>();
    var sources = new LinkedHashSet<SourceRef>();
    try {
      visit(
          request.targetId(),
          request.quantity(),
          catalog,
          new HashMap<>(),
          totals,
          new HashSet<>(),
          gaps,
          new LinkedHashSet<>(),
          sources);
      visit(
          request.targetId(),
          request.quantity(),
          catalog,
          owned,
          missing,
          new HashSet<>(),
          gaps,
          pre,
          sources);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("材料数量溢出", e);
    }
    var lines = new ArrayList<MaterialExpansion.MaterialLine>();
    for (var entry : totals.entrySet()) {
      var entity = catalog.get(entry.getKey());
      long need = missing.getOrDefault(entry.getKey(), 0L);
      lines.add(
          new MaterialExpansion.MaterialLine(
              entry.getKey(),
              entity == null ? entry.getKey() : entity.name(),
              entry.getValue(),
              request.owned() == null ? 0 : request.owned().getOrDefault(entry.getKey(), 0L),
              need,
              entity == null ? List.of() : strings(entity.attributes().path("acquisition"))));
    }
    return new MaterialExpansion(
        request.targetId(),
        request.quantity(),
        lines,
        List.copyOf(pre),
        List.copyOf(gaps),
        List.copyOf(sources));
  }

  private void visit(
      String id,
      long n,
      Map<String, GameEntity> catalog,
      Map<String, Long> owned,
      Map<String, Long> leaves,
      Set<String> path,
      Set<String> gaps,
      Set<String> pre,
      Set<SourceRef> sources) {
    if (path.size() > 100) throw new IllegalArgumentException("配方依赖过深");
    if (!path.add(id)) throw new IllegalArgumentException("配方循环依赖: " + id);
    var e = catalog.get(id);
    if (e == null) {
      gaps.add("未知材料或配方: " + id);
      leaves.merge(id, n, Math::addExact);
      path.remove(id);
      return;
    }
    if (e.source() != null) sources.add(e.source());
    long have = owned.getOrDefault(id, 0L), use = Math.min(have, n);
    owned.put(id, have - use);
    n -= use;
    if (n == 0) {
      path.remove(id);
      return;
    }
    var ingredients = e.attributes().get("ingredients");
    if ("recipe".equals(e.type())
        && (ingredients == null || !ingredients.isObject() || ingredients.isEmpty()))
      gaps.add("缺少配方: " + id);
    if (ingredients != null && ingredients.isObject() && !ingredients.isEmpty()) {
      pre.addAll(strings(e.attributes().path("prerequisites")));
      int output = e.attributes().path("outputQuantity").asInt(1);
      if (output < 1) throw new IllegalArgumentException("配方产量非法");
      long batches = n / output + (n % output == 0 ? 0 : 1);
      var fields = ingredients.fields();
      while (fields.hasNext()) {
        var f = fields.next();
        long count = f.getValue().asLong();
        if (count < 1) throw new IllegalArgumentException("配方原料数量非法");
        visit(
            f.getKey(),
            Math.multiplyExact(batches, count),
            catalog,
            owned,
            leaves,
            path,
            gaps,
            pre,
            sources);
      }
    } else leaves.merge(id, n, Math::addExact);
    path.remove(id);
  }

  private List<String> strings(com.fasterxml.jackson.databind.JsonNode node) {
    var values = new ArrayList<String>();
    node.forEach(n -> values.add(n.asText()));
    return values;
  }
}
