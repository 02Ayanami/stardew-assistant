package dev.stardew.assistant.knowledge;

import dev.stardew.assistant.common.SourceRef;

public record GuideChunk(
    String chunkId,
    String title,
    String text,
    String topic,
    SourceRef source,
    String knowledgeKind) {
  public GuideChunk {
    knowledgeKind = knowledgeKind == null ? "RULE_FACT" : knowledgeKind;
    if (!java.util.Set.of("RULE_FACT", "PLAYER_ADVICE").contains(knowledgeKind))
      throw new IllegalArgumentException("知识类型非法");
    if (source != null
        && "PLAYER_GUIDE".equals(source.sourceType())
        && !"PLAYER_ADVICE".equals(knowledgeKind))
      throw new IllegalArgumentException("玩家攻略只能作为经验建议");
  }

  public GuideChunk(String chunkId, String title, String text, String topic, SourceRef source) {
    this(chunkId, title, text, topic, source, null);
  }
}
