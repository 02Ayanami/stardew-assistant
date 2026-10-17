package dev.stardew.assistant.domain;

public record FishRequest(
    String season, String weather, String location, Integer time, String fishName) {}
