package dev.stardew.assistant.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import dev.stardew.assistant.data.*;
import dev.stardew.assistant.knowledge.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "assistant.embedding-dimension=32",
      "assistant.milvus-collection=stardew_simple_test_guides",
      "assistant.api-key="
    })
@Import(TestProviders.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationIT {
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.4").withDatabaseName("simple_test");

  static {
    MYSQL.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", MYSQL::getJdbcUrl);
    r.add("spring.datasource.username", MYSQL::getUsername);
    r.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired DataImportService importer;
  @Autowired GameDataRepository data;
  @Autowired GameMapper mapper;
  @Autowired VectorStore vectors;
  @Autowired GuideSearchService guides;
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper json;

  @BeforeAll
  void prepare() {
    assertThat(importer.importData(Path.of("../data/game"))).isTrue();
  }

  String session() {
    return http.postForObject("/api/sessions", Map.of(), JsonNode.class).path("id").asText();
  }

  JsonNode tool(String name, Map<String, Object> args) {
    var response =
        http.postForEntity(
            "/api/sessions/" + session() + "/tools/" + name, Map.of("args", args), JsonNode.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    return response.getBody().path("toolResults").get(0).path("result");
  }

  @Test
  void importsRealMysqlAndRealMilvusAndSkipsIdenticalData() {
    assertThat(data.all("all")).hasSize(322);
    assertThat(vectors.count()).isEqualTo(169);
    assertThat(data.find(new EntityQuery("crop", "防风草")))
        .singleElement()
        .extracting(GameEntity::id)
        .isEqualTo("parsnip");
    assertThat(data.find(new EntityQuery("crop", "Parsnip"))).hasSize(1);
    assertThat(importer.importData(Path.of("../data/game"))).isFalse();
    assertThat(guides.search("春天种植", null, Duration.ofSeconds(30)).data()).isNotEmpty();
  }

  @Test
  void fiveToolsHaveActualBusinessResults() {
    var crop =
        tool(
            "calculateCropProfit",
            Map.of(
                "cropIds",
                List.of("parsnip"),
                "start",
                Map.of("year", 1, "season", "spring", "day", 1),
                "end",
                Map.of("year", 1, "season", "spring", "day", 9),
                "quantity",
                2,
                "budget",
                40));
    assertThat(crop.path("data").get(0).path("netProfit").asLong()).isEqualTo(60);
    assertThat(
            tool(
                    "expandMaterialRequirements",
                    Map.of("targetId", "sprinkler", "quantity", 2, "owned", Map.of()))
                .path("data")
                .path("materials")
                .size())
        .isGreaterThan(0);
    assertThat(
            tool(
                    "findFish",
                    Map.of("season", "summer", "weather", "sun", "location", "ocean", "time", 1200))
                .path("data")
                .path("fish")
                .size())
        .isGreaterThan(0);
    assertThat(
            tool("queryGiftPreferences", Map.of("villager", "haley", "item", "prismatic-shard"))
                .path("data")
                .path("preferences")
                .get(0)
                .path("level")
                .asText())
        .isEqualTo("hate");
    var bundle =
        data.all("bundle").stream()
            .filter(e -> e.attributes().path("gold").asLong() == 2500)
            .findFirst()
            .orElseThrow();
    assertThat(
            tool("getBundleMissing", Map.of("bundle", bundle.id(), "gold", 2400))
                .path("data")
                .path("goldMissing")
                .asLong())
        .isEqualTo(100);
  }

  @Test
  void synchronousChatStoresCompleteHistoryAndRealToolSources() {
    String id = session();
    var response =
        http.postForEntity(
            "/api/sessions/" + id + "/messages", Map.of("message", "查作物"), JsonNode.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().path("sources").size()).isGreaterThan(0);
    var saved = http.getForObject("/api/sessions/" + id, JsonNode.class);
    assertThat(saved.path("messages")).hasSize(2);
    assertThat(saved.path("messages").get(1).path("toolResults").size()).isEqualTo(1);
    assertThat(http.getForObject("/api/sessions", JsonNode.class).path("items").size())
        .isGreaterThan(0);
  }

  @Test
  void invalidFractionalInputCannotSilentlyBecomeInteger() {
    assertThat(
            tool("calculateCropProfit", Map.of("quantity", 1.5, "budget", 40))
                .path("status")
                .asText())
        .isEqualTo("NEEDS_INPUT");
  }

  @Test
  void emptySessionIsNotInHistory() {
    String id = session();
    var page = http.getForObject("/api/sessions", JsonNode.class);
    for (var item : page.path("items")) assertThat(item.path("id").asText()).isNotEqualTo(id);
  }
}
