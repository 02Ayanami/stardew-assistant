package dev.stardew.assistant.data;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface GameMapper {
  List<String> all(@Param("type") String type);

  List<String> find(@Param("type") String type, @Param("query") String query);

  void clearAliases();

  void clearEntities();

  void insertEntity(
      @Param("id") String id,
      @Param("type") String type,
      @Param("name") String name,
      @Param("content") String content);

  void insertAlias(@Param("id") String id, @Param("name") String name);

  Map<String, Object> metadata();

  void beginImport();

  void ready(
      @Param("dataset") String dataset,
      @Param("version") String version,
      @Param("hash") String hash,
      @Param("model") String model,
      @Param("dimension") int dimension,
      @Param("entities") int entities,
      @Param("guides") int guides);

  void failed();
}
