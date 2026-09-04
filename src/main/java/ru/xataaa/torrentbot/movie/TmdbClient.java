package ru.xataaa.torrentbot.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.SafeLog;
import ru.xataaa.torrentbot.config.TmdbProperties;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Slf4j
@Component
public class TmdbClient {

    private final TmdbProperties tmdbProperties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TmdbClient(TmdbProperties tmdbProperties, MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.tmdbProperties = tmdbProperties;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(tmdbProperties.connectTimeoutMs()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public List<TmdbSearchResponse.TmdbSearchResult> searchMulti(String query) {
        if (!tmdbProperties.enabled()) {
            throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "TMDb API key is not configured");
        }
        String queryHash = SafeLog.sha256Short(query);
        Timer.Sample sample = Timer.start(meterRegistry);
        long startedAt = System.nanoTime();
        log.info("tmdb_search_started: queryHash={}, queryPreview={}", queryHash, SafeLog.preview(query, 40));
        try {
            TmdbSearchResponse response = authorizedGet("/search/multi", List.of(
                    param("language", tmdbProperties.language()),
                    param("query", query),
                    param("include_adult", "false")
            ), TmdbSearchResponse.class);
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
            log.warn("tmdb_search_failed: queryHash={}, durationMs={}, error={}, cause={}",
                    queryHash, durationMs, runtimeException.getMessage(), rootCause(runtimeException));
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
            TmdbTvDetailsResponse response = authorizedGet("/tv/" + tmdbId, List.of(
                    param("language", tmdbProperties.language())
            ), TmdbTvDetailsResponse.class);
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
            log.warn("tmdb_tv_seasons_failed: tmdbIdHash={}, durationMs={}, error={}, cause={}",
                    tmdbIdHash, durationMs, runtimeException.getMessage(), rootCause(runtimeException));
            throw runtimeException;
        }
    }

    public TmdbSeasonResponse tvSeason(String tmdbId, int seasonNumber) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long startedAt = System.nanoTime();
        String tmdbIdHash = SafeLog.sha256Short(tmdbId);
        log.info("tmdb_tv_season_started: tmdbIdHash={}, season={}", tmdbIdHash, seasonNumber);
        try {
            TmdbSeasonResponse response = authorizedGet("/tv/" + tmdbId + "/season/" + seasonNumber, List.of(
                    param("language", tmdbProperties.language())
            ), TmdbSeasonResponse.class);
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
            log.warn("tmdb_tv_season_failed: tmdbIdHash={}, season={}, durationMs={}, error={}, cause={}",
                    tmdbIdHash, seasonNumber, durationMs, runtimeException.getMessage(), rootCause(runtimeException));
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

    private <T> T authorizedGet(String path, List<QueryParam> queryParams, Class<T> responseType) {
        if (!tmdbProperties.enabled()) {
            throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "TMDb API key is not configured");
        }
        String apiCredential = tmdbProperties.apiKey().trim();
        boolean bearerToken = isBearerToken(apiCredential);
        List<QueryParam> allParams = bearerToken
                ? queryParams
                : concat(queryParams, param("api_key", apiCredential));
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri(path, allParams))
                .timeout(Duration.ofMillis(tmdbProperties.requestTimeoutMs()))
                .GET();
        if (bearerToken) {
            requestBuilder.header("Authorization", "Bearer " + apiCredential);
        }
        HttpRequest request = requestBuilder.build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("TMDb HTTP status " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException ioException) {
            throw new IllegalStateException("TMDb request failed", ioException);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TMDb request interrupted", interruptedException);
        }
    }

    private URI uri(String path, List<QueryParam> queryParams) {
        StringBuilder uri = new StringBuilder(trimTrailingSlash(tmdbProperties.baseUrl())).append(path);
        if (!queryParams.isEmpty()) {
            uri.append('?');
            for (int index = 0; index < queryParams.size(); index++) {
                if (index > 0) {
                    uri.append('&');
                }
                QueryParam param = queryParams.get(index);
                uri.append(encode(param.name())).append('=').append(encode(param.value()));
            }
        }
        return URI.create(uri.toString());
    }

    private List<QueryParam> concat(List<QueryParam> first, QueryParam second) {
        return java.util.stream.Stream.concat(first.stream(), java.util.stream.Stream.of(second)).toList();
    }

    private QueryParam param(String name, Object value) {
        return new QueryParam(name, value == null ? "" : value.toString());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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

    private String rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return cause.getClass().getSimpleName() + ": " + SafeLog.preview(message, 160);
    }

    private record QueryParam(String name, String value) {
    }
}
