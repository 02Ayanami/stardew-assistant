package dev.stardew.assistant.crop;

import java.util.List;

public record CropProjection(
    String cropId,
    String name,
    List<HarvestEvent> harvests,
    long totalCost,
    long totalRevenue,
    long netProfit,
    boolean feasible,
    String reason) {}
