package dev.stardew.assistant.domain;

public record GiftPreference(
    String villagerId,
    String villagerName,
    String itemId,
    String itemName,
    String level,
    String rule) {}
