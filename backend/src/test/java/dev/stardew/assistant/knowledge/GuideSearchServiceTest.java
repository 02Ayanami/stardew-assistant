package dev.stardew.assistant.knowledge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.stardew.assistant.common.AppException;
import dev.stardew.assistant.data.GameDataRepository;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class GuideSearchServiceTest {
  @Test
  void modelOrDimensionMismatchDoesNotSearchOldVectors() {
    var data = mock(GameDataRepository.class);
    var e = mock(EmbeddingClient.class);
    var v = mock(VectorStore.class);
    when(data.metadata()).thenReturn(Map.of("embedding_model", "old", "dimension", 32));
    when(e.model()).thenReturn("new");
    when(e.dimensions()).thenReturn(32);
    var search = new GuideSearchService(data, e, v, .35f);
    assertThatThrownBy(() -> search.search("问题", null, Duration.ofSeconds(30)))
        .isInstanceOfSatisfying(
            AppException.class, x -> assertThat(x.code).isEqualTo("EMBEDDING_MISMATCH"));
    verifyNoInteractions(v);
  }

  @Test
  void unknownTopicRequestsCorrectionWithoutEmbeddingCharges() {
    var data = mock(GameDataRepository.class);
    var embed = mock(EmbeddingClient.class);
    var vectors = mock(VectorStore.class);
    var search = new GuideSearchService(data, embed, vectors, .35f);
    var result = search.search("作物换季", "季节与作物枯萎", Duration.ofSeconds(30));
    assertThat(result.status()).isEqualTo(dev.stardew.assistant.common.ToolStatus.NEEDS_INPUT);
    verifyNoInteractions(data, embed, vectors);
  }
}
