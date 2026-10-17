package dev.stardew.assistant.knowledge;

import dev.stardew.assistant.common.SourceRef;

public record GuideHit(
    String chunkId,
    String title,
    String text,
    float score,
    SourceRef source,
    String knowledgeKind) {}
