package dev.stardew.assistant.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.common.*;
import dev.stardew.assistant.knowledge.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DataImportService {
  private final GameMapper mapper;
  private final ObjectMapper json;
  private final EmbeddingClient embeddings;
  private final VectorStore vectors;
  private final TransactionTemplate tx;

  public DataImportService(
      GameMapper mapper,
      ObjectMapper json,
      EmbeddingClient embeddings,
      VectorStore vectors,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.json = json;
    this.embeddings = embeddings;
    this.vectors = vectors;
    this.tx = new TransactionTemplate(manager);
  }

  public synchronized boolean importData(Path directory) {
    try {
      byte[] entityBytes = Files.readAllBytes(directory.resolve("entities.json")),
          guideBytes = Files.readAllBytes(directory.resolve("guides.jsonl"));
      var manifest = json.readTree(directory.resolve("manifest.json").toFile());
      String dataset = manifest.path("snapshotId").asText(),
          version = manifest.path("gameVersion").asText();
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      digest.update(entityBytes);
      digest.update(guideBytes);
      digest.update(json.writeValueAsBytes(manifest));
      String hash = HexFormat.of().formatHex(digest.digest());
      var meta = mapper.metadata();
      if ("READY".equals(meta.get("status"))
          && hash.equals(meta.get("content_hash"))
          && embeddings.model().equals(meta.get("embedding_model"))
          && embeddings.dimensions() == ((Number) meta.get("dimension")).intValue()
          && vectors.count() == ((Number) meta.get("guide_count")).intValue()) return false;
      var entities = List.of(json.readValue(entityBytes, GameEntity[].class));
      var chunks = new ArrayList<GuideChunk>();
      var ids = new HashSet<String>();
      if (entities.size() != manifest.path("entityCount").asInt())
        throw new IllegalArgumentException("实体数量不符");
      for (var e : entities) {
        if (e.id() == null
            || e.name() == null
            || e.type() == null
            || e.attributes() == null
            || !ids.add(e.id())) throw new IllegalArgumentException("实体数据非法或重复");
        SourceValidation.validate(e.source(), dataset, version);
        SourceValidation.nested(e.attributes(), dataset, version, json);
      }
      ids.clear();
      for (String line :
          new String(guideBytes, java.nio.charset.StandardCharsets.UTF_8)
              .lines()
              .filter(s -> !s.isBlank())
              .toList()) {
        var d = json.readValue(line, GuideDocument.class);
        if (d.id() == null
            || d.title() == null
            || d.text() == null
            || d.text().isBlank()
            || d.text().length() > 800
            || !ids.add(d.id())) throw new IllegalArgumentException("攻略数据非法或重复");
        SourceValidation.validate(d.source(), dataset, version);
        chunks.add(
            new GuideChunk(d.id(), d.title(), d.text(), d.topic(), d.source(), d.knowledgeKind()));
      }
      if (chunks.size() != manifest.path("guideCount").asInt())
        throw new IllegalArgumentException("攻略数量不符");
      mapper.beginImport();
      vectors.rebuild();
      for (int i = 0; i < chunks.size(); i += 10) {
        var batch = chunks.subList(i, Math.min(i + 10, chunks.size()));
        vectors.upsert(
            batch,
            embeddings.embed(
                batch.stream().map(GuideChunk::text).toList(), Duration.ofSeconds(120)));
      }
      if (vectors.count() != chunks.size()) throw new IllegalStateException("索引数量不符");
      tx.executeWithoutResult(
          t -> {
            mapper.clearAliases();
            mapper.clearEntities();
            for (var e : entities) {
              try {
                mapper.insertEntity(e.id(), e.type(), e.name(), json.writeValueAsString(e));
                for (var alias :
                    new LinkedHashSet<>(e.aliases() == null ? List.<String>of() : e.aliases()))
                  mapper.insertAlias(e.id(), alias);
              } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
              }
            }
            mapper.ready(
                dataset,
                version,
                hash,
                embeddings.model(),
                embeddings.dimensions(),
                entities.size(),
                chunks.size());
          });
      return true;
    } catch (Exception e) {
      mapper.failed();
      throw new IllegalStateException("资料导入失败：" + e.getMessage(), e);
    }
  }
}
