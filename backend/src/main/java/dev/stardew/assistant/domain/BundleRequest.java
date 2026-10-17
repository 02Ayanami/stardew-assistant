package dev.stardew.assistant.domain;

public record BundleRequest(
    String bundle,
    java.util.List<BundleItem> submitted,
    java.util.List<BundleItem> owned,
    Long gold) {}
