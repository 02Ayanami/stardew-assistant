package dev.stardew.assistant.domain;

public record BundleMissing(
    String bundleId,
    String bundleName,
    int requiredSlots,
    int completedSlots,
    int remainingSlots,
    java.util.List<BundleGap> options,
    long goldRequired,
    long goldMissing) {}
