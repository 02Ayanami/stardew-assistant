package dev.stardew.assistant.domain;

/** One distinct bundle slot; repeated item IDs can belong to different slots. */
public record BundleGap(
    String itemId,
    String name,
    long quantity,
    String quality,
    long submitted,
    long usableOwned,
    long missing,
    String slotId) {
  public BundleGap(
      String itemId,
      String name,
      long quantity,
      String quality,
      long submitted,
      long usableOwned,
      long missing) {
    this(itemId, name, quantity, quality, submitted, usableOwned, missing, null);
  }
}
