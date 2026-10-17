package dev.stardew.assistant.material;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.common.SourceRef;
import dev.stardew.assistant.data.GameEntity;
import java.util.*;
import org.junit.jupiter.api.Test;

class MaterialExpanderTest {
  GameEntity entity(String id, String type, String json) {
    try {
      return new GameEntity(
          id,
          id,
          type,
          List.of(),
          new ObjectMapper().readTree(json),
          new SourceRef(
              id, id, "https://example.com/" + id, "fixture", null, "2026-10-10", "fixture"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  Map<String, GameEntity> catalog() {
    return Map.of(
        "a",
        entity("a", "recipe", "{\"ingredients\":{\"b\":2,\"wood\":3}}"),
        "b",
        entity("b", "recipe", "{\"ingredients\":{\"wood\":4}}"),
        "wood",
        entity("wood", "item", "{\"acquisition\":[\"采集\"]}"));
  }

  @Test
  void sharedMaterialsAreMerged() {
    var r =
        new MaterialExpander().expand(new MaterialRequest("a", 2, Map.of("wood", 5L)), catalog());
    assertEquals(22, r.materials().get(0).totalRequired());
    assertEquals(17, r.materials().get(0).missingQuantity());
  }

  @Test
  void ownedIntermediateIsConsumedBeforeExpansion() {
    var r = new MaterialExpander().expand(new MaterialRequest("a", 2, Map.of("b", 1L)), catalog());
    assertEquals(18, r.materials().get(0).missingQuantity());
  }

  @Test
  void cycleReturnsDataError() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MaterialExpander()
                .expand(
                    new MaterialRequest("a", 1, Map.of()),
                    Map.of("a", entity("a", "recipe", "{\"ingredients\":{\"a\":1}}"))));
  }

  @Test
  void missingRecipeReturnsGap() {
    var r = new MaterialExpander().expand(new MaterialRequest("unknown", 1, Map.of()), Map.of());
    assertFalse(r.gaps().isEmpty());
  }

  @Test
  void rejectsNegativeOwnedQuantity() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MaterialExpander()
                .expand(new MaterialRequest("a", 1, Map.of("wood", -1L)), catalog()));
  }

  @Test
  void rejectsOverflowMaterialTotal() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MaterialExpander()
                .expand(new MaterialRequest("a", Long.MAX_VALUE, Map.of()), catalog()));
  }
}
