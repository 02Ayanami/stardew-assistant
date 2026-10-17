package dev.stardew.assistant.ai;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class ModelHttp {
  private ModelHttp() {}

  public static RestClient.Builder client(Duration timeout) {
    long total = Math.max(2, timeout.toMillis()), connect = Math.min(5000, total / 2);
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(connect));
    factory.setReadTimeout(Duration.ofMillis(total - connect));
    return RestClient.builder().requestFactory(factory);
  }

  public static dev.stardew.assistant.common.AppException failure(Exception error, String code) {
    Throwable cause = error;
    for (int i = 0; i < 12 && cause != null; i++, cause = cause.getCause()) {
      if (cause instanceof java.net.SocketTimeoutException
          || cause instanceof java.net.http.HttpTimeoutException)
        return new dev.stardew.assistant.common.AppException(504, "TIMEOUT", "模型或嵌入请求超时，请稍后重试");
    }
    return new dev.stardew.assistant.common.AppException(502, code, "模型服务请求失败，请检查权限、额度或网络后重试");
  }
}
