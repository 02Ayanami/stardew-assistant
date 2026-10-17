package dev.stardew.assistant.common;

import java.util.List;

/** Provenance travels with facts and survives snapshot restoration. */
public record SourceRef(
    String sourceId,
    String title,
    String url,
    String gameVersion,
    String revision,
    String capturedAt,
    String snapshotId,
    String sourceType,
    String language,
    String sourceFamily,
    String author,
    String publishedAt,
    List<String> applicableVersions) {
  public SourceRef {
    sourceType = sourceType == null ? "WIKI" : sourceType;
    language =
        language == null ? (url != null && url.contains("zh.stardew") ? "zh" : "en") : language;
    sourceFamily = sourceFamily == null ? "stardew-wiki" : sourceFamily;
    applicableVersions =
        applicableVersions == null
            ? List.of(gameVersion == null ? "unknown" : gameVersion)
            : List.copyOf(applicableVersions);
  }

  public SourceRef(
      String sourceId,
      String title,
      String url,
      String gameVersion,
      String revision,
      String capturedAt,
      String snapshotId) {
    this(
        sourceId,
        title,
        url,
        gameVersion,
        revision,
        capturedAt,
        snapshotId,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
