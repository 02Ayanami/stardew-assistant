package dev.stardew.assistant.crop;

import dev.stardew.assistant.common.GameDate;
import java.util.*;

public record CropScenario(
    List<String> cropIds,
    GameDate start,
    GameDate end,
    Long quantity,
    Long budget,
    Map<String, Object> rules) {}
