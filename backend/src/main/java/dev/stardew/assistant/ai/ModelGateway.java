package dev.stardew.assistant.ai;

import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.tool.ToolCallback;

public interface ModelGateway {
  AssistantMessage call(List<Message> messages, List<ToolCallback> tools, Duration timeout);
}
