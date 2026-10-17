package dev.stardew.assistant.tools;

import dev.stardew.assistant.common.ToolResult;
import java.util.Map;

public record ToolExecution(String name, Map<String, Object> args, ToolResult<?> result) {}
