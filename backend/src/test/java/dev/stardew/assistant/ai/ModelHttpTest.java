package dev.stardew.assistant.ai;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ModelHttpTest {
  @Test
  void distinguishesTransportTimeoutFromOtherProviderFailures() {
    assertThat(
            ModelHttp.failure(
                    new RuntimeException(new java.net.SocketTimeoutException()), "MODEL_FAILED")
                .status)
        .isEqualTo(504);
    assertThat(ModelHttp.failure(new RuntimeException("network"), "MODEL_FAILED").status)
        .isEqualTo(502);
  }
}
