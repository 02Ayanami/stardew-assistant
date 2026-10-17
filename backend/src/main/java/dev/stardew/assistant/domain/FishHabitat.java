package dev.stardew.assistant.domain;

public record FishHabitat(
    java.util.List<String> seasons,
    java.util.List<String> weather,
    java.util.List<String> locations,
    int startTime,
    int endTime) {}
