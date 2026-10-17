package dev.stardew.assistant.ai;

import dev.stardew.assistant.common.AppException;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.zhipuai.*;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ZhipuModelGateway implements ModelGateway {
  private final String key, model;

  public ZhipuModelGateway(
      @Value("${assistant.api-key}") String key, @Value("${assistant.chat-model}") String model) {
    this.key = key;
    this.model = model;
  }

  public AssistantMessage call(List<Message> messages, List<ToolCallback> tools, Duration timeout) {
    if (key.isBlank() || model.isBlank())
      throw new AppException(503, "MODEL_NOT_CONFIGURED", "请在新项目 .env 中配置 API Key 和聊天模型");
    var api = ZhiPuAiApi.builder().apiKey(key).restClientBuilder(ModelHttp.client(timeout)).build();
    var options =
        ZhiPuAiChatOptions.builder()
            .model(model)
            .temperature(0.1)
            .maxTokens(6000)
            .toolCallbacks(tools)
            .internalToolExecutionEnabled(false)
            .build();
    var client =
        new ZhiPuAiChatModel(
            api,
            options,
            org.springframework.retry.support.RetryTemplate.builder().maxAttempts(1).build());
    try {
      return client.call(new Prompt(messages, options)).getResult().getOutput();
    } catch (Exception e) {
      throw ModelHttp.failure(e, "MODEL_FAILED");
    }
  }
}
