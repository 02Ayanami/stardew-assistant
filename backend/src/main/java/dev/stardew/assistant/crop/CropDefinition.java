package dev.stardew.assistant.crop;

import java.util.List;

public record CropDefinition(
    String id,
    String name,
    int growthDays,
    int regrowDays,
    List<String> seasons,
    long seedPrice,
    long sellPrice,
    int yieldPerHarvest) {}
