package dev.stardew.assistant.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class GameDataRepository {
  private final GameMapper mapper;
  private final ObjectMapper json;

  public GameDataRepository(GameMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  public List<GameEntity> all(String type) {
    requireReady();
    return mapper.all(type).stream().map(this::decode).toList();
  }

  public List<GameEntity> find(EntityQuery query) {
    requireReady();
    if (query.idOrName() == null || query.idOrName().isBlank())
      throw new IllegalArgumentException("请输入名称");
    return mapper
        .find(query.type() == null ? "all" : query.type(), query.idOrName().trim())
        .stream()
        .map(this::decode)
        .toList();
  }

  public GameEntity get(String id) {
    var matches = find(new EntityQuery("all", id));
    if (matches.size() != 1) throw new IllegalArgumentException("名称不存在或有歧义：" + id);
    return matches.get(0);
  }

  public Map<String, Object> metadata() {
    return mapper.metadata();
  }

  public void requireReady() {
    if (!"READY".equals(metadata().get("status")))
      throw new dev.stardew.assistant.common.AppException(503, "DATA_NOT_READY", "请先完成资料导入");
  }

  public String gameVersion() {
    requireReady();
    return String.valueOf(metadata().get("game_version"));
  }

  private GameEntity decode(String text) {
    try {
      return json.readValue(text, GameEntity.class);
    } catch (Exception e) {
      throw new IllegalStateException("游戏资料损坏", e);
    }
  }
}
