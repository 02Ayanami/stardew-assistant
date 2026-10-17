package dev.stardew.assistant.conversation;

import dev.stardew.assistant.common.SourceRef;
import dev.stardew.assistant.tools.ToolExecution;
import java.util.List;

public record TurnResponse(
    String sessionId,
    long messageId,
    String answer,
    List<ToolExecution> toolResults,
    List<SourceRef> sources) {}
