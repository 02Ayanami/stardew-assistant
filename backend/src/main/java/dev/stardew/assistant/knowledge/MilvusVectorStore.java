package dev.stardew.assistant.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import io.milvus.v2.client.*;
import io.milvus.v2.common.*;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.FloatVec;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MilvusVectorStore implements VectorStore {
  private final String uri, collection;
  private final int dimension;
  private final ObjectMapper json;

  public MilvusVectorStore(
      @Value("${assistant.milvus-uri}") String uri,
      @Value("${assistant.milvus-collection}") String collection,
      @Value("${assistant.embedding-dimension}") int dimension,
      ObjectMapper json) {
    if (!collection.startsWith("stardew_simple_"))
      throw new IllegalArgumentException("新项目只能使用 stardew_simple_ 索引");
    this.uri = uri;
    this.collection = collection;
    this.dimension = dimension;
    this.json = json;
  }

  private MilvusClientV2 client(Duration timeout) {
    long millis = Math.max(1, timeout.toMillis());
    return new MilvusClientV2(
        ConnectConfig.builder()
            .uri(uri)
            .connectTimeoutMs(Math.min(5000, millis))
            .rpcDeadlineMs(millis)
            .build());
  }

  public void rebuild() {
    var c = client(Duration.ofSeconds(30));
    try {
      if (c.hasCollection(HasCollectionReq.builder().collectionName(collection).build()))
        c.dropCollection(DropCollectionReq.builder().collectionName(collection).build());
      c.createCollection(
          CreateCollectionReq.builder()
              .collectionName(collection)
              .dimension(dimension)
              .primaryFieldName("id")
              .idType(DataType.VarChar)
              .maxLength(256)
              .vectorFieldName("vector")
              .metricType("COSINE")
              .autoID(false)
              .enableDynamicField(true)
              .consistencyLevel(ConsistencyLevel.STRONG)
              .build());
    } finally {
      c.close();
    }
  }

  public void upsert(List<GuideChunk> chunks, List<float[]> vectors) {
    if (chunks.size() != vectors.size()) throw new IllegalArgumentException("文本与向量数量不同");
    var rows = new ArrayList<JsonObject>();
    for (int i = 0; i < chunks.size(); i++) {
      if (vectors.get(i).length != dimension) throw new IllegalArgumentException("向量维度不符");
      var chunk = chunks.get(i);
      var row = new JsonObject();
      row.addProperty("id", chunk.chunkId());
      row.addProperty("topic", chunk.topic());
      try {
        row.addProperty("content", json.writeValueAsString(chunk));
      } catch (Exception e) {
        throw new IllegalArgumentException(e);
      }
      var vector = new JsonArray();
      for (float v : vectors.get(i)) {
        if (!Float.isFinite(v)) throw new IllegalArgumentException("非法向量");
        vector.add(v);
      }
      row.add("vector", vector);
      rows.add(row);
    }
    var c = client(Duration.ofSeconds(30));
    try {
      c.upsert(UpsertReq.builder().collectionName(collection).data(rows).build());
    } finally {
      c.close();
    }
  }

  public int count() {
    var c = client(Duration.ofSeconds(30));
    try {
      if (!c.hasCollection(HasCollectionReq.builder().collectionName(collection).build())) return 0;
      return ((Number)
              c.query(
                      QueryReq.builder()
                          .collectionName(collection)
                          .outputFields(List.of("count(*)"))
                          .consistencyLevel(ConsistencyLevel.STRONG)
                          .build())
                  .getQueryResults()
                  .get(0)
                  .getEntity()
                  .get("count(*)"))
          .intValue();
    } finally {
      c.close();
    }
  }

  public List<GuideHit> search(float[] vector, String topic, Duration timeout) {
    if (vector.length != dimension) throw new IllegalArgumentException("向量维度不符");
    long deadline = System.nanoTime() + timeout.toNanos();
    var c = client(timeout);
    try {
      c.loadCollection(LoadCollectionReq.builder().collectionName(collection).build());
      var builder =
          SearchReq.builder()
              .collectionName(collection)
              .annsField("vector")
              .data(List.of(new FloatVec(vector)))
              .topK(5)
              .outputFields(List.of("content"))
              .consistencyLevel(ConsistencyLevel.STRONG);
      if (topic != null && !topic.isBlank()) builder.filter("topic == " + new Gson().toJson(topic));
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0)
        throw new dev.stardew.assistant.common.AppException(504, "TIMEOUT", "检索超时");
      var response =
          c.withTimeout(remaining, java.util.concurrent.TimeUnit.NANOSECONDS)
              .search(builder.build());
      var hits = new ArrayList<GuideHit>();
      if (!response.getSearchResults().isEmpty())
        for (var hit : response.getSearchResults().get(0)) {
          try {
            var chunk =
                json.readValue(String.valueOf(hit.getEntity().get("content")), GuideChunk.class);
            hits.add(
                new GuideHit(
                    chunk.chunkId(),
                    chunk.title(),
                    chunk.text(),
                    hit.getScore(),
                    chunk.source(),
                    chunk.knowledgeKind()));
          } catch (Exception e) {
            throw new IllegalStateException("索引内容损坏", e);
          }
        }
      return hits;
    } finally {
      c.close();
    }
  }
}
