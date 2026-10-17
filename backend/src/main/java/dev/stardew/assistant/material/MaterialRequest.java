package dev.stardew.assistant.material;

import java.util.Map;

public record MaterialRequest(String targetId, long quantity, Map<String, Long> owned) {}
