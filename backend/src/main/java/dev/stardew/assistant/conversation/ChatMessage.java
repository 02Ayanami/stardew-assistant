package dev.stardew.assistant.conversation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.common.SourceRef;
import dev.stardew.assistant.tools.ToolExecution;
import java.time.Instant;
import java.util.List;

public class ChatMessage {
  public Long id;
  public String sessionId, role, content;
  public Instant createdAt;
  @JsonIgnore public String toolResultsJson = "[]", sourcesJson = "[]";

  public List<ToolExecution> getToolResults() {
    try {
      return JSON.readValue(
          toolResultsJson,
          JSON.getTypeFactory().constructCollectionType(List.class, ToolExecution.class));
    } catch (Exception e) {
      throw new IllegalStateException("工具历史损坏", e);
    }
  }

  public List<SourceRef> getSources() {
    try {
      return JSON.readValue(
          sourcesJson, JSON.getTypeFactory().constructCollectionType(List.class, SourceRef.class));
    } catch (Exception e) {
      throw new IllegalStateException("来源历史损坏", e);
    }
  }

  private static final ObjectMapper JSON = new ObjectMapper();
}
