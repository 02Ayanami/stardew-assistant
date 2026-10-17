package dev.stardew.assistant.common;

import java.util.List;

public record ToolResult<T>(
    ToolStatus status,
    T data,
    List<SourceRef> sources,
    List<String> assumptions,
    String errorCode) {
  public static <T> ToolResult<T> ok(T data, List<SourceRef> sources) {
    return new ToolResult<>(ToolStatus.SUCCESS, data, List.copyOf(sources), List.of(), null);
  }

  public static <T> ToolResult<T> fail(ToolStatus status, String error) {
    return new ToolResult<>(status, null, List.of(), List.of(), error);
  }
}
