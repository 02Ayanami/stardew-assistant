package dev.stardew.assistant.knowledge;

import dev.stardew.assistant.ai.ModelHttp;
import java.time.Duration;
import java.util.*;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.zhipuai.*;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ZhipuEmbeddingClient implements EmbeddingClient {
  private final String key, model;
  private final int dimensions;

  public ZhipuEmbeddingClient(
      @Value("${assistant.api-key}") String key,
      @Value("${assistant.embedding-model}") String model,
      @Value("${assistant.embedding-dimension}") int dimensions) {
    this.key = key;
    this.model = model;
    this.dimensions = dimensions;
  }

  public String model() {
    return model;
  }

  public int dimensions() {
    return dimensions;
  }

  public List<float[]> embed(List<String> texts, Duration timeout) {
    if (key.isBlank() || model.isBlank())
      throw new dev.stardew.assistant.common.AppException(
          503, "EMBEDDING_NOT_CONFIGURED", "请配置嵌入模型与 API Key");
    var api = ZhiPuAiApi.builder().apiKey(key).restClientBuilder(ModelHttp.client(timeout)).build();
    var client =
        new ZhiPuAiEmbeddingModel(
            api,
            MetadataMode.EMBED,
            ZhiPuAiEmbeddingOptions.builder().model(model).dimensions(dimensions).build(),
            org.springframework.retry.support.RetryTemplate.builder().maxAttempts(1).build());
    List<float[]> result;
    try {
      result = client.embed(texts);
    } catch (Exception e) {
      throw ModelHttp.failure(e, "EMBEDDING_FAILED");
    }
    if (result.size() != texts.size()) throw new IllegalStateException("嵌入结果数量不符");
    for (var vector : result) {
      if (vector.length != dimensions) throw new IllegalArgumentException("嵌入维度不符");
      for (float v : vector) if (!Float.isFinite(v)) throw new IllegalArgumentException("嵌入数值非法");
    }
    return result;
  }
}
