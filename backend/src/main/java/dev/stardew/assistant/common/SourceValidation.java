package dev.stardew.assistant.common;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.time.LocalDate;

public final class SourceValidation {
  private SourceValidation() {}

  public static void validate(SourceRef s, String snapshot, String version) {
    if (s == null
        || !snapshot.equals(s.snapshotId())
        || blank(s.sourceId())
        || blank(s.title())
        || blank(s.url())
        || blank(s.gameVersion())
        || blank(s.revision())
        || blank(s.capturedAt())
        || !versionKey(version).equals(versionKey(s.gameVersion())))
      throw new IllegalArgumentException("来源缺少元数据或快照版本不符");
    try {
      var uri = URI.create(s.url());
      if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
        throw new IllegalArgumentException("来源 URL 非法");
      LocalDate.parse(s.capturedAt());
      if (!java.util.Set.of("WIKI", "OFFICIAL_CHANGELOG", "PLAYER_GUIDE").contains(s.sourceType())
          || blank(s.language())
          || blank(s.sourceFamily())
          || s.applicableVersions().isEmpty()) throw new IllegalArgumentException("来源分类或适用版本缺失");
      if (s.applicableVersions().stream()
          .anyMatch(v -> blank(v) || !compatible(v, s.gameVersion())))
        throw new IllegalArgumentException("来源适用版本与快照不兼容");
      if (!"WIKI".equals(s.sourceType()) && !s.revision().matches("sha256:[0-9a-f]{64}"))
        throw new IllegalArgumentException("非Wiki来源必须保存SHA256内容哈希");
      if ("PLAYER_GUIDE".equals(s.sourceType()) && (blank(s.author()) || blank(s.publishedAt())))
        throw new IllegalArgumentException("玩家攻略缺少作者或发布日期");
      if (s.publishedAt() != null) LocalDate.parse(s.publishedAt());
    } catch (java.time.DateTimeException e) {
      throw new IllegalArgumentException("来源日期非法", e);
    }
  }

  private static boolean compatible(String declared, String game) {
    String a = versionKey(declared), b = versionKey(game);
    if (a.equals(b)) return true;
    // A snapshot family (1.6.x) can contain explicitly scoped 1.6/1.6.0 evidence,
    // but never admits another minor-version family such as 1.5.
    if (!a.matches("[0-9]+\\.[0-9]+(?:\\.(?:[0-9]+|x))?")
        || !b.matches("[0-9]+\\.[0-9]+(?:\\.(?:[0-9]+|x))?")) return false;
    var av = a.split("\\.");
    var bv = b.split("\\.");
    return av[0].equals(bv[0])
        && av[1].equals(bv[1])
        && (av.length < 3
            || bv.length < 3
            || av[2].equals("x")
            || bv[2].equals("x")
            || av[2].equals(bv[2]));
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String versionKey(String s) {
    if (blank(s)) throw new IllegalArgumentException("版本缺失");
    return s.split("[（(]", 2)[0].trim();
  }

  public static void nested(JsonNode node, String snapshot, String version, ObjectMapper mapper) {
    if (node == null) return;
    if (node.isObject())
      node.fields()
          .forEachRemaining(
              e -> {
                if (e.getKey().equals("source") || e.getKey().endsWith("Source"))
                  validate(mapper.convertValue(e.getValue(), SourceRef.class), snapshot, version);
                else nested(e.getValue(), snapshot, version, mapper);
              });
    else if (node.isArray()) node.forEach(n -> nested(n, snapshot, version, mapper));
  }
}
