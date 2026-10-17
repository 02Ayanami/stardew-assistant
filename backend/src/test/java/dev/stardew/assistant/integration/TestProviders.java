package dev.stardew.assistant.integration;

import dev.stardew.assistant.ai.ModelGateway;
import dev.stardew.assistant.knowledge.EmbeddingClient;
import java.util.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Test-only providers. Never packaged in the production jar. */
@TestConfiguration
public class TestProviders {
  @Bean
  @Primary
  EmbeddingClient fixtureEmbeddings() {
    return new EmbeddingClient() {
      public String model() {
        return "fixture-only";
      }

      public int dimensions() {
        return 32;
      }

      public List<float[]> embed(List<String> texts, java.time.Duration timeout) {
        return texts.stream()
            .map(
                text -> {
                  float[] v = new float[32];
                  v[0] = 1;
                  for (int i = 0; i < text.length(); i++)
                    v[1 + Math.floorMod(text.charAt(i), 31)] += 0.01f;
                  return v;
                })
            .toList();
      }
    };
  }

  @Bean
  @Primary
  ModelGateway fixtureModel() {
    return (messages, tools, timeout) -> {
      var last = messages.get(messages.size() - 1);
      if (last instanceof ToolResponseMessage)
        return new AssistantMessage("已根据实际工具结果整理回答，请查看下方结果和来源。");
      String question = last.getText();
      if (question.contains("查作物"))
        return AssistantMessage.builder()
            .content("")
            .toolCalls(
                List.of(new AssistantMessage.ToolCall("fixture", "function", "listCrops", "{}")))
            .build();
      return new AssistantMessage("这是测试模型的回答，用于验证页面和历史保存。生产版本使用你配置的真实模型。");
    };
  }
}
