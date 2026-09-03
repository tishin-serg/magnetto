package ru.xataaa.torrentbot.torrentsearch;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.SafeLog;
import ru.xataaa.torrentbot.config.JacredProperties;
import ru.xataaa.torrentbot.config.WebClientConfig;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Slf4j
@Component
public class JacredClient {

    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    private final JacredProperties jacredProperties;
    private final WebClient webClient;

    public JacredClient(JacredProperties jacredProperties, WebClient.Builder webClientBuilder) {
        this.jacredProperties = jacredProperties;
        this.webClient = webClientBuilder
                .baseUrl(jacredProperties.baseUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                        .build())
                .clientConnector(WebClientConfig.connector(jacredProperties.connectTimeoutMs(), jacredProperties.requestTimeoutMs()))
                .build();
    }

    public boolean healthCheck() {
        String response = webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(jacredProperties.requestTimeoutMs()));
        return response != null;
    }

    public List<JacredSearchResult> search(TorrentSearchRequest request, boolean fallbackQuerySearch) {
        if (!jacredProperties.enabled()) {
            throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "JacRed API key is not configured");
        }
        String queryHash = SafeLog.sha256Short(request.query());
        String searchType = fallbackQuerySearch ? "fallback" : "structured";
        long startedAt = System.nanoTime();
        log.info("jacred_client_search_started: queryHash={}, type={}", queryHash, searchType);
        try {
            int timeoutMs = fallbackQuerySearch
                    ? jacredProperties.fallbackRequestTimeoutMs()
                    : jacredProperties.requestTimeoutMs();
            JacredSearchResponse response = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/v2.0/indexers/all/results")
                                .queryParam("apikey", jacredProperties.apiKey());
                        if (fallbackQuerySearch) {
                            uriBuilder.queryParam("Query", request.query());
                        } else {
                            if (request.title() != null && !request.title().isBlank()) {
                                uriBuilder.queryParam("title", request.title());
                            }
                            if (request.originalTitle() != null && !request.originalTitle().isBlank()) {
                                uriBuilder.queryParam("title_original", request.originalTitle());
                            }
                            if (request.year() != null) {
                                uriBuilder.queryParam("year", request.year());
                            }
                            if (request.serialType() != null) {
                                uriBuilder.queryParam("is_serial", request.serialType());
                            }
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .bodyToMono(JacredSearchResponse.class)
                    .block(Duration.ofMillis(timeoutMs));
            List<JacredSearchResult> results = response == null || response.getResults() == null
                    ? Collections.emptyList()
                    : response.getResults();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            log.info("jacred_client_search_completed: queryHash={}, type={}, resultCount={}, durationMs={}",
                    queryHash, searchType, results.size(), durationMs);
            return results;
        } catch (RuntimeException runtimeException) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            log.warn("jacred_client_search_failed: queryHash={}, type={}, durationMs={}, error={}",
                    queryHash, searchType, durationMs, runtimeException.getMessage());
            throw runtimeException;
        }
    }
}
