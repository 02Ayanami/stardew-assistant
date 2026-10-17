package dev.stardew.assistant.knowledge;

import dev.stardew.assistant.common.*;
import dev.stardew.assistant.data.GameDataRepository;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GuideSearchService {
  private final GameDataRepository data;
  private final EmbeddingClient embeddings;
  private final VectorStore store;
  private final float minimum;

  public GuideSearchService(
      GameDataRepository data,
      EmbeddingClient embeddings,
      VectorStore store,
      @Value("${assistant.min-score}") float minimum) {
    if (!Float.isFinite(minimum) || minimum < -1 || minimum > 1)
      throw new IllegalArgumentException("检索分数非法");
    this.data = data;
    this.embeddings = embeddings;
    this.store = store;
    this.minimum = minimum;
  }

  public ToolResult<Map<String, Object>> search(String text, String topic, Duration timeout) {
    if (topic != null
        && !Set.of("crop", "material", "fish", "gift", "bundle", "version", "advice")
            .contains(topic))
      return ToolResult.fail(
          ToolStatus.NEEDS_INPUT,
          "TOPIC_MUST_BE_crop_material_fish_gift_bundle_version_advice_OR_OMITTED");
    data.requireReady();
    var meta = data.metadata();
    if (!embeddings.model().equals(meta.get("embedding_model"))
        || embeddings.dimensions() != ((Number) meta.get("dimension")).intValue())
      throw new AppException(503, "EMBEDDING_MISMATCH", "嵌入模型或维度已改变，请停止应用并重新导入资料");
    if (text == null || text.isBlank())
      return ToolResult.fail(ToolStatus.NEEDS_INPUT, "QUESTION_REQUIRED");
    long deadline = System.nanoTime() + timeout.toNanos();
    var vectors = embeddings.embed(List.of(text), timeout);
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) throw new AppException(504, "TIMEOUT", "检索超时");
    var hits =
        store.search(vectors.get(0), topic, Duration.ofNanos(remaining)).stream()
            .filter(h -> Float.isFinite(h.score()) && h.score() >= minimum && h.source() != null)
            .limit(5)
            .toList();
    return new ToolResult<>(
        hits.isEmpty() ? ToolStatus.NO_DATA : ToolStatus.SUCCESS,
        Map.of("hits", hits),
        hits.stream().map(GuideHit::source).distinct().toList(),
        List.of("向量相似度只表示检索相关程度，不保证结论正确"),
        hits.isEmpty() ? "NO_SUPPORTING_GUIDE" : null);
  }
}
