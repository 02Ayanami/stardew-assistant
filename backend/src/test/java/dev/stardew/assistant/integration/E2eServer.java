package dev.stardew.assistant.integration;

import dev.stardew.assistant.AssistantApplication;
import dev.stardew.assistant.data.*;
import java.nio.file.Path;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.containers.MySQLContainer;

/** Isolated browser-test server: real disposable MySQL + real new-project Milvus, test-only AI. */
public class E2eServer {
  public static void main(String[] args) {
    var mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("simple_browser_test");
    mysql.start();
    Runtime.getRuntime().addShutdownHook(new Thread(mysql::stop));
    var app = new SpringApplication(AssistantApplication.class, TestProviders.class);
    var context =
        app.run(
            "--server.port=9921",
            "--spring.datasource.url=" + mysql.getJdbcUrl(),
            "--spring.datasource.username=" + mysql.getUsername(),
            "--spring.datasource.password=" + mysql.getPassword(),
            "--assistant.embedding-dimension=32",
            "--assistant.milvus-collection=stardew_simple_browser_guides",
            "--assistant.api-key=",
            "--assistant.chat-model=test-fixture");
    context.getBean(DataImportService.class).importData(Path.of("../data/game"));
  }

  @RestController
  static class ReadyController {
    final GameDataRepository data;

    ReadyController(GameDataRepository data) {
      this.data = data;
    }

    @GetMapping("/api/e2e-ready")
    ResponseEntity<?> ready() {
      return ResponseEntity.status("READY".equals(data.metadata().get("status")) ? 200 : 503)
          .body(Map.of("testOnly", true));
    }
  }
}
