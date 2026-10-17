package dev.stardew.assistant.knowledge;

import java.time.Duration;
import java.util.List;

public interface EmbeddingClient {
  List<float[]> embed(List<String> texts, Duration timeout);

  String model();

  int dimensions();
}
