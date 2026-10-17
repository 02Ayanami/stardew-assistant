package dev.stardew.assistant.knowledge;

import java.time.Duration;
import java.util.List;

public interface VectorStore {
  void rebuild();

  void upsert(List<GuideChunk> chunks, List<float[]> vectors);

  int count();

  List<GuideHit> search(float[] vector, String topic, Duration timeout);
}
