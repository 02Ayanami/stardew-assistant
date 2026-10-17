package dev.stardew.assistant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

@Component
public class ToolDefinitions {
  private final List<ToolCallback> callbacks;

  public ToolDefinitions(ObjectMapper json) throws java.io.IOException {
    var entries = new ArrayList<ToolCallback>();
    try (var stream = getClass().getResourceAsStream("/tools.json")) {
      for (var entry : json.readTree(stream)) {
        var definition =
            ToolDefinition.builder()
                .name(entry.path("name").asText())
                .description(entry.path("description").asText())
                .inputSchema(entry.path("schema").toString())
                .build();
        entries.add(
            new ToolCallback() {
              public ToolDefinition getToolDefinition() {
                return definition;
              }

              public String call(String input) {
                throw new UnsupportedOperationException("工具只由 ChatService 执行");
              }
            });
      }
    }
    callbacks = List.copyOf(entries);
  }

  public List<ToolCallback> callbacks() {
    return callbacks;
  }

  public boolean contains(String name) {
    return callbacks.stream().anyMatch(c -> c.getToolDefinition().name().equals(name));
  }
}
