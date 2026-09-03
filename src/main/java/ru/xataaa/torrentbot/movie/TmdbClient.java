package ru.xataaa.torrentbot.movie;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.SafeLog;
import ru.xataaa.torrentbot.config.TmdbProperties;
import ru.xataaa.torrentbot.config.WebClientConfig;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Slf4j
@Component
public class TmdbClient {

    private final TmdbProperties tmdbProperties;
    private final MeterRegistry meterRegistry;
    private final WebClient webClient;

    public TmdbClient(TmdbProperties tmdbProperties, MeterRegistry meterRegistry, WebClient.Builder webClientBuilder) {
        this.tmdbProperties = tmdbProperties;
        this.meterRegistry = meterRegistry;
        this.webClient = webClientBuilder
                .baseUrl(tmdbProperties.baseUrl())
                .clientConnector(WebClientConfig.connector(tmdbProperties.connectTimeoutMs(), tmdbProperties.requestTimeoutMs()))
                .build();
    }

    public List<TmdbSearchResponse.TmdbSearchResult> searchMulti(String query) {
        if (!tmdbProperties.enabled()) {
            throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "TMDb API key is not configured");
        }
        String apiCredential = tmdbProperties.apiKey().trim();
        boolean bearerToken = isBearerToken(apiCredential);
        String queryHash = SafeLog.sha256Short(query);
        Timer.Sample sample = Timer.start(meterRegistry);
        long startedAt = System.nanoTime();
        log.info("tmdb_search_started: queryHash={}, queryPreview={}", queryHash, SafeLog.preview(query, 40));
        try {
            TmdbSearchResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search/multi")
                            .queryParamIfPresent("api_key", bearerToken ? java.util.Optional.empty() : java.util.Optional.of(apiCredential))
                            .queryParam("language", tmdbProperties.language())
                            .queryParam("query", query)
                            .queryParam("include_adult", false)
                            .build())
                    .headers(headers -> {
                        if (bearerToken) {
                            headers.setBearerAuth(apiCredential);
                        }
                    })
                    .retrieve()
                    .bodyToMono(TmdbSearchResponse.class)
                    .timeout(Duration.ofMillis(tmdbProperties.requestTimeoutMs()))
                    .block();
            List<TmdbSearchResponse.TmdbSearchResult> results = response == null || response.getResults() == null
                    ? List.of()
                    : response.getResults();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            sample.stop(timer("success", results.isEmpty() ? "empty" : "found"));
            log.info("tmdb_search_completed: queryHash={}, resultCount={}, durationMs={}", queryHash, results.size(), durationMs);
            return results;
        } catch (RuntimeException runtimeException) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            sample.stop(timer("error", "error"));
            meterRegistry.counter("search.error", "source", "tmdb", "result", "error").increment();
            log.warn("tmdb_search_failed: queryHash={}, durationMs={}, error={}", queryHash, durationMs, runtimeException.getMessage());
            throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "TMDb search failed", runtimeException);
        }
    }

    public String posterUrl(String posterPath) {
        if (posterPath == null || posterPath.isBlank()) {
            return "";
        }
        return trimTrailingSlash(tmdbProperties.imageBaseUrl()) + posterPath;
    }

    public List<TmdbTvDetailsResponse.TmdbSeason> tvSeasons(String tmdbId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long startedAt = System.nanoTime();
        String tmdbIdHash = SafeLog.sha256Short(tmdbId);
        log.info("tmdb_tv_seasons_started: tmdbIdHash={}", tmdbIdHash);
        try {
            TmdbTvDetailsResponse response = authorizedGet("/tv/" + tmdbId, TmdbTvDetailsResponse.class);
            List<TmdbTvDetailsResponse.TmdbSeason> seasons = response == null || response.getSeasons() == null
                    ? Collections.emptyList()
                    : response.getSeasons();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            sample.stop(Timer.builder("tmdb.details.duration")
                    .tag("source", "tmdb")
                    .tag("result", seasons.isEmpty() ? "empty" : "success")
                    .tag("type", "tv_seasons")
                    .register(meterRegistry));
            log.info("tmdb_tv_seasons_completed: tmdbIdHash={}, resultCount={}, durationMs={}", tmdbIdHash, seasons.size(), durationMs);
            return seasons;
        } catch (RuntimeException runtimeException) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            sample.stop(Timer.builder("tmdb.details.duration")
                    .tag("source", "tmdb")
                    .tag("result", "error")
                    .tag("type", "tv_seasons")
                    .register(meterRegistry));
            log.warn("tmdb_tv_seasons_failed: tmdbIdHash={}, durationMs={}, error={}", tmdbIdHash, durationMs, runtimeException.getMessage());
            throw runtimeException;
        }
    }

    public TmdbSeasonResponse tvSeason(String tmdbId, int seasonNumber) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long startedAt = System.nanoTime();
        String tmdbIdHash = SafeLog.sha256Short(tmdbId);
        log.info("tmdb_tv_season_started: tmdbIdHash={}, season={}", tmdbIdHash, seasonNumber);
        try {
            TmdbSeasonResponse response = authorizedGet("/tv/" + tmdbId + "/season/" + seasonNumber, TmdbSeasonResponse.class);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            sample.stop(Timer.builder("tmdb.details.duration")
                    .tag("source", "tmdb")
                    .tag("result", response == null ? "empty" : "success")
                    .tag("type", "tv_season")
                    .register(meterRegistry));
            int episodeCount = response == null || response.getEpisodes() == null ? 0 : response.getEpisodes().size();
            log.info("tmdb_tv_season_completed: tmdbIdHash={}, season={}, episodeCount={}, durationMs={}", tmdbIdHash, seasonNumber, episodeCount, durationMs);
            return response;
        } catch (RuntimeException runtimeException) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            sample.stop(Timer.builder("tmdb.details.duration")
                    .tag("source", "tmdb")
                    .tag("result", "error")
                    .tag("type", "tv_season")
                    .register(meterRegistry));
            log.warn("tmdb_tv_season_failed: tmdbIdHash={}, season={}, durationMs={}, error={}", tmdbIdHash, seasonNumber, durationMs, runtimeException.getMessage());
            throw runtimeException;
        }
    }

    private Timer timer(String result, String type) {
        return Timer.builder("tmdb.search.duration")
                .tag("source", "tmdb")
                .tag("result", result)
                .tag("type", type)
                .register(meterRegistry);
    }

    private <T> T authorizedGet(String path, Class<T> responseType) {
        if (!tmdbProperties.enabled()) {
            throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "TMDb API key is not configured");
        }
        String apiCredential = tmdbProperties.apiKey().trim();
        boolean bearerToken = isBearerToken(apiCredential);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path(path)
                        .queryParamIfPresent("api_key", bearerToken ? java.util.Optional.empty() : java.util.Optional.of(apiCredential))
                        .queryParam("language", tmdbProperties.language())
                        .build())
                .headers(headers -> {
                    if (bearerToken) {
                        headers.setBearerAuth(apiCredential);
                    }
                })
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofMillis(tmdbProperties.requestTimeoutMs()))
                .block();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean isBearerToken(String apiCredential) {
        return apiCredential.startsWith("eyJ") || apiCredential.length() > 80;
    }
}
