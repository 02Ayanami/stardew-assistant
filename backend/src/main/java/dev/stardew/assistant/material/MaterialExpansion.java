package dev.stardew.assistant.material;

import dev.stardew.assistant.common.SourceRef;
import java.util.*;

public record MaterialExpansion(
    String targetId,
    long quantity,
    List<MaterialLine> materials,
    List<String> prerequisites,
    List<String> gaps,
    List<SourceRef> sources) {
  public record MaterialLine(
      String id,
      String name,
      long totalRequired,
      long ownedQuantity,
      long missingQuantity,
      List<String> acquisition) {}
}
