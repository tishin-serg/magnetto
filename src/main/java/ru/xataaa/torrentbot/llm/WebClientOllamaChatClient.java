package ru.xataaa.torrentbot.llm;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.config.LlmProperties;
import ru.xataaa.torrentbot.config.WebClientConfig;

@Component
@RequiredArgsConstructor
public class WebClientOllamaChatClient implements OllamaChatClient {

    private final WebClient.Builder webClientBuilder;
    private final LlmProperties llmProperties;
    private final LlmRouterJsonSchema llmRouterJsonSchema;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        WebClient webClient = webClientBuilder.clone()
                .clientConnector(WebClientConfig.connector(
                        llmProperties.timeoutSeconds() * 1000,
                        llmProperties.timeoutSeconds() * 1000
                ))
                .build();
        Map<String, Object> request = Map.of(
                "model", llmProperties.ollamaModel(),
                "stream", false,
                "format", llmRouterJsonSchema.schema(),
                "options", Map.of(
                        "temperature", 0,
                        "top_p", 0.8
                ),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        OllamaChatResponse response = webClient.post()
                .uri(llmProperties.ollamaUrl())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaChatResponse.class)
                .block();
        if (response == null || response.message() == null) {
            return "";
        }
        return response.message().content();
    }

    private record OllamaChatResponse(OllamaMessage message) {
    }

    private record OllamaMessage(String content) {
    }
}
