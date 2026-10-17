package dev.stardew.assistant.common;

public class AppException extends RuntimeException {
  public final int status;
  public final String code;

  public AppException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}
