package dev.stardew.assistant.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.common.*;
import dev.stardew.assistant.data.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DomainToolsTest {
  GameDataRepository repository = mock(GameDataRepository.class);
  DomainTools tools = new DomainTools(repository);
  String snapshot = "test";

  DomainToolsTest() {
    when(repository.gameVersion()).thenReturn("1.6");
  }

  GameEntity entity(String id, String type, String json) throws Exception {
    return new GameEntity(
        id,
        id,
        type,
        List.of(),
        new ObjectMapper().readTree(json),
        new SourceRef(
            id, id, "https://example.com/" + id, "1.6", "123456", "2026-10-11", snapshot));
  }

  void catalog(String type, GameEntity... entities) {
    when(repository.all(type)).thenReturn(List.of(entities));
  }

  @Test
  void fishKeepsHabitatAlternativesTogether() throws Exception {
    catalog(
        "fish",
        entity(
            "fish",
            "fish",
            """
      {"habitats":[{"seasons":["spring"],"weather":["rain"],"locations":["river"],"startTime":600,"endTime":1900},
      {"seasons":["summer"],"weather":["sun"],"locations":["ocean"],"startTime":900,"endTime":2200}]}
      """));
    assertThat(tools.findFish(new FishRequest("spring", "sun", "ocean", 1000, null)).status())
        .isEqualTo(ToolStatus.NO_DATA);
    var hit = tools.findFish(new FishRequest("spring", "rain", "river", 600, null));
    assertThat(hit.data().fish()).hasSize(1);
    assertThat(hit.data().fish().get(0).habitats()).hasSize(1);
    assertThat(tools.findFish(new FishRequest(null, null, null, 2361, null)).status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
    assertThat(tools.findFish(new FishRequest("monsoon", null, null, null, null)).status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
  }

  @Test
  void personalGiftExceptionOverridesUniversalAndCategory() throws Exception {
    catalog("item", entity("diamond", "item", "{\"giftCategories\":[\"gem\"]}"));
    catalog(
        "villager",
        entity(
            "abigail",
            "villager",
            "{\"giftPreferences\":{\"personal\":{\"hate\":[\"diamond\"]}}}"));
    catalog(
        "gift-rules",
        entity(
            "universal",
            "gift-rules",
            "{\"universal\":{\"love\":[\"diamond\"]},\"universalCategories\":{\"like\":[\"gem\"]}}"));
    var result = tools.queryGiftPreferences(new GiftRequest("abigail", "diamond", null));
    assertThat(result.data().preferences())
        .singleElement()
        .extracting(GiftPreference::level)
        .isEqualTo("hate");
    assertThat(tools.queryGiftPreferences(new GiftRequest("abigail", "diamond", "love")).status())
        .isEqualTo(ToolStatus.NO_DATA);
    assertThat(tools.queryGiftPreferences(new GiftRequest("unknown", null, null)).status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
  }

  @Test
  void bundleQualityQuantityAndDistinctChoices() throws Exception {
    catalog(
        "item", entity("a", "item", "{}"), entity("b", "item", "{}"), entity("c", "item", "{}"));
    catalog(
        "bundle",
        entity(
            "quality",
            "bundle",
            """
      {"mode":"normal","requiredSlots":2,"requirements":[{"itemId":"a","quantity":5,"quality":"gold"},
      {"itemId":"b","quantity":1,"quality":"normal"},{"itemId":"c","quantity":1,"quality":"normal"}],"gold":0}
      """));
    var result =
        tools.getBundleMissing(
            new BundleRequest(
                "quality",
                List.of(new BundleItem("a", 2L, "gold")),
                List.of(
                    new BundleItem("a", 20L, "silver"),
                    new BundleItem("a", 2L, "iridium"),
                    new BundleItem("b", 10L, "normal")),
                0L));
    assertThat(result.data().completedSlots()).isZero();
    assertThat(result.data().remainingSlots()).isEqualTo(2);
    assertThat(result.data().options().get(0).missing()).isEqualTo(1);
    assertThat(result.data().options().get(1).missing()).isZero();
    var submitted =
        tools.getBundleMissing(
            new BundleRequest(
                "quality", List.of(new BundleItem("b", 10L, "normal")), List.of(), 0L));
    assertThat(submitted.data().completedSlots()).isEqualTo(1);
    assertThat(submitted.data().remainingSlots()).isEqualTo(1);
    assertThat(
            tools
                .getBundleMissing(
                    new BundleRequest(
                        "quality", List.of(new BundleItem("a", 1L, "silver")), List.of(), 0L))
                .status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
  }

  @Test
  void vaultGoldAndMissingInputs() throws Exception {
    catalog(
        "bundle",
        entity(
            "vault",
            "bundle",
            "{\"mode\":\"normal\",\"requiredSlots\":0,\"requirements\":[],\"gold\":2500}"));
    assertThat(
            tools
                .getBundleMissing(new BundleRequest("vault", List.of(), List.of(), 2400L))
                .data()
                .goldMissing())
        .isEqualTo(100);
    assertThat(
            tools.getBundleMissing(new BundleRequest("vault", List.of(), List.of(), null)).status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
    assertThat(tools.getBundleMissing(new BundleRequest("vault", null, null, -1L)).status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
  }

  @Test
  void bundleRejectsNullQualityAndUnknownInventoryWithoutThrowing() throws Exception {
    catalog("item", entity("a", "item", "{}"));
    catalog(
        "bundle",
        entity(
            "bundle",
            "bundle",
            "{\"mode\":\"normal\",\"requiredSlots\":1,\"requirements\":[{\"itemId\":\"a\",\"quantity\":1,\"quality\":\"normal\"}],\"gold\":0}"));
    assertThat(
            tools
                .getBundleMissing(
                    new BundleRequest(
                        "bundle", List.of(), List.of(new BundleItem("a", 1L, null)), null))
                .status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
    assertThat(
            tools
                .getBundleMissing(
                    new BundleRequest(
                        "bundle",
                        List.of(),
                        List.of(new BundleItem("unknown", 1L, "normal")),
                        null))
                .status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
  }

  @Test
  void giftCategoriesApplyOnlyWhenNoPersonalItemAndUnknownNeverDefaultsNeutral() throws Exception {
    catalog(
        "item",
        entity("a", "item", "{\"giftCategories\":[\"vegetable\"]}"),
        entity("b", "item", "{}"));
    catalog(
        "villager",
        entity(
            "v",
            "villager",
            "{\"giftPreferences\":{\"personalCategories\":{\"hate\":[\"vegetable\"]}}}"));
    catalog(
        "gift-rules",
        entity(
            "universal",
            "gift-rules",
            "{\"universal\":{\"love\":[\"a\"]},\"universalCategories\":{\"like\":[\"vegetable\"]}}"));
    assertThat(tools.queryGiftPreferences(new GiftRequest("v", "a", null)).data().preferences())
        .singleElement()
        .extracting(GiftPreference::level)
        .isEqualTo("hate");
    assertThat(tools.queryGiftPreferences(new GiftRequest("v", "b", null)).status())
        .isEqualTo(ToolStatus.NO_DATA);
  }

  @Test
  void malformedFishDataIsUnsupportedAndAliasLookupIsExact() throws Exception {
    catalog(
        "fish",
        entity(
            "a",
            "fish",
            "{\"habitats\":[{\"seasons\":[\"spring\"],\"weather\":[\"any\"],\"locations\":[\"river\"],\"startTime\":600}]}"));
    assertThat(tools.findFish(new FishRequest(null, null, null, null, "a")).status())
        .isEqualTo(ToolStatus.UNSUPPORTED);
    assertThat(tools.findFish(new FishRequest(null, null, null, null, "not-a")).status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
  }

  @Test
  void chooseNCountsDistinctSubmittedAndRejectsDuplicateInventory() throws Exception {
    catalog("item", entity("a", "item", "{}"), entity("b", "item", "{}"));
    catalog(
        "bundle",
        entity(
            "bundle",
            "bundle",
            "{\"mode\":\"normal\",\"requiredSlots\":1,\"requirements\":[{\"itemId\":\"a\",\"quantity\":1,\"quality\":\"normal\"},{\"itemId\":\"b\",\"quantity\":1,\"quality\":\"normal\"}],\"gold\":0}"));
    assertThat(
            tools
                .getBundleMissing(
                    new BundleRequest(
                        "bundle", List.of(new BundleItem("a", 1L, "normal")), List.of(), null))
                .data()
                .remainingSlots())
        .isZero();
    assertThat(
            tools
                .getBundleMissing(
                    new BundleRequest(
                        "bundle",
                        List.of(),
                        List.of(
                            new BundleItem("a", 1L, "normal"), new BundleItem("a", 2L, "normal")),
                        null))
                .status())
        .isEqualTo(ToolStatus.NEEDS_INPUT);
  }

  @Test
  void constructionRepeatedWoodSlotsConsumeEachInventoryUnitOnce() throws Exception {
    catalog("item", entity("wood", "item", "{}"));
    catalog(
        "bundle",
        entity(
            "construction",
            "bundle",
            "{\"mode\":\"normal\",\"requiredSlots\":2,\"requirements\":[{\"itemId\":\"wood\",\"quantity\":99,\"quality\":\"normal\"},{\"itemId\":\"wood\",\"quantity\":99,\"quality\":\"normal\"}],\"gold\":0}"));
    var owned =
        tools.getBundleMissing(
            new BundleRequest(
                "construction", List.of(), List.of(new BundleItem("wood", 99L, "normal")), null));
    assertThat(owned.status()).isEqualTo(ToolStatus.SUCCESS);
    assertThat(owned.data().options()).hasSize(2);
    assertThat(owned.data().options().stream().mapToLong(BundleGap::usableOwned).sum())
        .isEqualTo(99);
    assertThat(owned.data().options().stream().mapToLong(BundleGap::missing).sum()).isEqualTo(99);
    var submitted =
        tools.getBundleMissing(
            new BundleRequest(
                "construction", List.of(new BundleItem("wood", 198L, "normal")), List.of(), null));
    assertThat(submitted.data().completedSlots()).isEqualTo(2);
    assertThat(submitted.data().remainingSlots()).isZero();
  }

  @Test
  void repeatedItemQualitySlotsAllocateGoldBeforeNormal() throws Exception {
    catalog("item", entity("a", "item", "{}"));
    catalog(
        "bundle",
        entity(
            "mixed",
            "bundle",
            "{\"mode\":\"normal\",\"requiredSlots\":2,\"requirements\":[{\"itemId\":\"a\",\"quantity\":1,\"quality\":\"normal\"},{\"itemId\":\"a\",\"quantity\":1,\"quality\":\"gold\"}],\"gold\":0}"));
    var owned =
        tools.getBundleMissing(
            new BundleRequest(
                "mixed",
                List.of(),
                List.of(new BundleItem("a", 1L, "gold"), new BundleItem("a", 1L, "normal")),
                null));
    assertThat(owned.status()).isEqualTo(ToolStatus.SUCCESS);
    assertThat(owned.data().options()).allMatch(g -> g.missing() == 0);
    var submitted =
        tools.getBundleMissing(
            new BundleRequest(
                "mixed",
                List.of(new BundleItem("a", 1L, "gold")),
                List.of(new BundleItem("a", 1L, "normal")),
                null));
    assertThat(submitted.data().completedSlots()).isEqualTo(1);
    assertThat(submitted.data().options().get(1).submitted()).isEqualTo(1);
    assertThat(submitted.data().options()).allMatch(g -> g.missing() == 0);
  }

  @Test
  void domainFactsFailClosedOnMissingInvalidSourceOrUnsupportedVersion() throws Exception {
    var valid =
        entity(
            "a",
            "fish",
            "{\"habitats\":[{\"seasons\":[\"spring\"],\"weather\":[\"any\"],\"locations\":[\"river\"],\"startTime\":600,\"endTime\":2600}]}");
    for (var source :
        Arrays.asList(
            null,
            new SourceRef(
                "a", "a", "http://example.com/a", "1.6", "123456", "2026-10-11", snapshot),
            new SourceRef(
                "a", "a", "https://example.com/a", "1.5", "123456", "2026-10-11", snapshot))) {
      catalog(
          "fish",
          new GameEntity(
              valid.id(), valid.name(), valid.type(), valid.aliases(), valid.attributes(), source));
      assertThat(tools.findFish(new FishRequest(null, null, null, null, "a")).status())
          .isEqualTo(ToolStatus.UNSUPPORTED);
    }
  }

  @Test
  void goldBundleNeedsOnlyAvailableGoldNotItemInventory() throws Exception {
    catalog(
        "bundle",
        entity(
            "vault",
            "bundle",
            "{\"mode\":\"normal\",\"requiredSlots\":0,\"requirements\":[],\"gold\":2500}"));
    var result = tools.getBundleMissing(new BundleRequest("vault", null, null, 1000L));
    assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
    assertThat(result.data().goldMissing()).isEqualTo(1500);
  }
}
