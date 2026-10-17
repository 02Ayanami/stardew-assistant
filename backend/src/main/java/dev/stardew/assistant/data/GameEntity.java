package dev.stardew.assistant.data;

import com.fasterxml.jackson.databind.JsonNode;
import dev.stardew.assistant.common.SourceRef;
import java.util.List;

public record GameEntity(
    String id,
    String name,
    String type,
    List<String> aliases,
    JsonNode attributes,
    SourceRef source) {}
