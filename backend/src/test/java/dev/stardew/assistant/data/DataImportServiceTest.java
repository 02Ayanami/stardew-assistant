package dev.stardew.assistant.data;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stardew.assistant.knowledge.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class DataImportServiceTest {
  @Test
  void failedEmbeddingMarksNotReadyAndCanBeRetried() {
    var db = mock(GameMapper.class);
    var embed = mock(EmbeddingClient.class);
    var store = mock(VectorStore.class);
    when(db.metadata()).thenReturn(Map.of("status", "EMPTY"));
    when(embed.model()).thenReturn("fixture");
    when(embed.dimensions()).thenReturn(32);
    when(embed.embed(anyList(), any())).thenThrow(new IllegalStateException("network"));
    var importer =
        new DataImportService(
            db, new ObjectMapper(), embed, store, mock(PlatformTransactionManager.class));
    assertThatThrownBy(() -> importer.importData(Path.of("../data/game")))
        .hasMessageContaining("导入失败");
    assertThatThrownBy(() -> importer.importData(Path.of("../data/game")))
        .hasMessageContaining("导入失败");
    verify(db, times(2)).failed();
    verify(store, times(2)).rebuild();
    verify(db, never())
        .ready(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void missingInputDoesNotDeleteExistingEntities() {
    var db = mock(GameMapper.class);
    when(db.metadata()).thenReturn(Map.of("status", "EMPTY"));
    var importer =
        new DataImportService(
            db,
            new ObjectMapper(),
            mock(EmbeddingClient.class),
            mock(VectorStore.class),
            mock(PlatformTransactionManager.class));
    assertThatThrownBy(() -> importer.importData(Path.of("does-not-exist")))
        .hasMessageContaining("导入失败");
    verify(db, never()).clearEntities();
  }

  @Test
  void successfulRetryRebuildsIndexAndMarksReady() {
    var db = mock(GameMapper.class);
    var embed = mock(EmbeddingClient.class);
    var store = mock(VectorStore.class);
    when(db.metadata()).thenReturn(Map.of("status", "FAILED"));
    when(embed.model()).thenReturn("fixture");
    when(embed.dimensions()).thenReturn(32);
    when(embed.embed(anyList(), any()))
        .thenAnswer(
            inv -> {
              List<String> texts = inv.getArgument(0);
              return texts.stream().map(t -> new float[32]).toList();
            });
    when(store.count()).thenReturn(169);
    var manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any()))
        .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
    var importer = new DataImportService(db, new ObjectMapper(), embed, store, manager);
    assertThat(importer.importData(Path.of("../data/game"))).isTrue();
    verify(db)
        .ready(anyString(), anyString(), anyString(), eq("fixture"), eq(32), eq(322), eq(169));
    verify(store).rebuild();
  }
}
