package dev.stardew.assistant.api;

import dev.stardew.assistant.common.AppException;
import java.util.Map;
import org.slf4j.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler(AppException.class)
  public ResponseEntity<?> app(AppException e) {
    return ResponseEntity.status(e.status).body(Map.of("code", e.code, "message", e.getMessage()));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  public ResponseEntity<?> input(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "INVALID_INPUT",
                "message",
                e instanceof IllegalArgumentException
                    ? String.valueOf(e.getMessage())
                    : "请求格式不正确"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> other(Exception e) {
    log.error("Request failed", e);
    return ResponseEntity.status(500)
        .body(Map.of("code", "INTERNAL_ERROR", "message", "服务处理失败，请查看后端日志并稍后重试"));
  }
}
