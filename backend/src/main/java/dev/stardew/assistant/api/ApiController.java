package dev.stardew.assistant.api;

import dev.stardew.assistant.conversation.*;
import dev.stardew.assistant.data.GameDataRepository;
import dev.stardew.assistant.tools.ToolService;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final ChatService chat;
  private final ToolService tools;
  private final GameDataRepository data;
  private final String key, model, embedding;
  private final int dimension;

  public ApiController(
      ChatService chat,
      ToolService tools,
      GameDataRepository data,
      @Value("${assistant.api-key}") String key,
      @Value("${assistant.chat-model}") String model,
      @Value("${assistant.embedding-model}") String embedding,
      @Value("${assistant.embedding-dimension}") int dimension) {
    this.chat = chat;
    this.tools = tools;
    this.data = data;
    this.key = key;
    this.model = model;
    this.embedding = embedding;
    this.dimension = dimension;
  }

  public record ChatInput(String message) {}

  public record ToolInput(Map<String, Object> args) {}

  @PostMapping("/sessions")
  public ChatSession create() {
    return chat.create();
  }

  @GetMapping("/sessions")
  public Map<String, Object> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int limit) {
    return chat.history(page, limit);
  }

  @GetMapping("/sessions/{id}")
  public Map<String, Object> session(@PathVariable String id) {
    return chat.session(id);
  }

  @PostMapping("/sessions/{id}/messages")
  public TurnResponse message(@PathVariable String id, @RequestBody ChatInput input) {
    return chat.chat(id, input.message());
  }

  @PostMapping("/sessions/{id}/tools/{name}")
  public TurnResponse tool(
      @PathVariable String id, @PathVariable String name, @RequestBody ToolInput input) {
    return chat.direct(id, name, input.args());
  }

  @GetMapping("/tool-options")
  public Map<String, Object> options(@RequestParam String tool) {
    return tools.options(tool);
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("status", "UP");
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of(
        "data",
        data.metadata(),
        "model",
        model,
        "embeddingModel",
        embedding,
        "dimension",
        dimension,
        "modelConfigured",
        !key.isBlank(),
        "retrieval",
        "vector");
  }
}
