package ru.xataaa.torrentbot.movie;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.SafeLog;
import ru.xataaa.torrentbot.config.TmdbProperties;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieMetadataService {

    private final TmdbClient tmdbClient;
    private final TmdbProperties tmdbProperties;
    private final MeterRegistry meterRegistry;
    private final Map<String, CacheEntry> queryCache = new ConcurrentHashMap<>();
    private final Map<String, MovieEntry> selectionCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<MovieMetadata>>> inFlightSearches = new ConcurrentHashMap<>();

    public List<MovieMetadata> search(String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.length() < 2) {
            return List.of();
        }
        cleanupExpiredEntries();
        String queryHash = SafeLog.sha256Short(normalizedQuery);
        Optional<List<MovieMetadata>> cachedResults = findCached(normalizedQuery);
        if (cachedResults.isPresent()) {
            meterRegistry.counter("tmdb.cache.hit", "source", "tmdb", "result", "success").increment();
            log.info("tmdb_cache_hit: queryHash={}, resultCount={}", queryHash, cachedResults.get().size());
            return cachedResults.get();
        }
        meterRegistry.counter("tmdb.cache.miss", "source", "tmdb", "result", "success").increment();
        log.info("tmdb_cache_miss: queryHash={}", queryHash);
        String cacheKey = normalizedQuery.toLowerCase();
        CompletableFuture<List<MovieMetadata>> currentSearch = new CompletableFuture<>();
        CompletableFuture<List<MovieMetadata>> existingSearch = inFlightSearches.putIfAbsent(cacheKey, currentSearch);
        if (existingSearch != null) {
            long waitTimeoutMs = Math.max(250L, tmdbProperties.requestTimeoutMs() + 250L);
            log.info("tmdb_singleflight_wait: queryHash={}, timeoutMs={}", queryHash, waitTimeoutMs);
            try {
                return existingSearch.get(waitTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeoutException) {
                log.warn("tmdb_singleflight_timeout: queryHash={}, timeoutMs={}", queryHash, waitTimeoutMs);
                throw new IllegalStateException("TMDb search is still running", timeoutException);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("TMDb search wait interrupted", interruptedException);
            } catch (ExecutionException executionException) {
                Throwable cause = executionException.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("TMDb search failed", executionException);
            }
        }
        try {
            List<MovieMetadata> results = searchExternalAndCache(normalizedQuery, queryHash);
            currentSearch.complete(results);
            return results;
        } catch (RuntimeException runtimeException) {
            currentSearch.completeExceptionally(runtimeException);
            throw runtimeException;
        } finally {
            inFlightSearches.remove(cacheKey, currentSearch);
        }
    }

    public Optional<List<MovieMetadata>> findCached(String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.length() < 2) {
            return Optional.empty();
        }
        cleanupExpiredEntries();
        CacheEntry cacheEntry = queryCache.get(normalizedQuery.toLowerCase());
        if (cacheEntry == null || cacheEntry.expiresAt().isBefore(Instant.now())) {
            queryCache.remove(normalizedQuery.toLowerCase());
            return Optional.empty();
        }
        return Optional.of(cacheEntry.results());
    }

    private List<MovieMetadata> searchExternalAndCache(String normalizedQuery, String queryHash) {
        List<MovieMetadata> results = tmdbClient.searchMulti(normalizedQuery).stream()
                .map(this::toMetadata)
                .flatMap(Optional::stream)
                .sorted(resultComparator(normalizedQuery))
                .limit(Math.max(1, tmdbProperties.maxInlineResults()))
                .toList();
        Instant expiresAt = Instant.now().plusSeconds(Math.max(60L, tmdbProperties.cacheTtlMinutes() * 60L));
        queryCache.put(normalizedQuery.toLowerCase(), new CacheEntry(results, expiresAt));
        for (MovieMetadata movieMetadata : results) {
            selectionCache.put(movieMetadata.selectionId(), new MovieEntry(movieMetadata, expiresAt));
        }
        log.info("tmdb_cache_store: queryHash={}, resultCount={}", queryHash, results.size());
        return results;
    }

    public Optional<MovieMetadata> findBySelectionId(String selectionId) {
        cleanupExpiredEntries();
        if (selectionId == null || selectionId.isBlank()) {
            return Optional.empty();
        }
        MovieEntry movieEntry = selectionCache.get(selectionId);
        if (movieEntry == null || movieEntry.expiresAt().isBefore(Instant.now())) {
            selectionCache.remove(selectionId);
            return Optional.empty();
        }
        return Optional.of(movieEntry.movieMetadata());
    }

    public int selectionCacheSize() {
        cleanupExpiredEntries();
        return selectionCache.size();
    }

    private Optional<MovieMetadata> toMetadata(TmdbSearchResponse.TmdbSearchResult result) {
        MovieMediaType mediaType = mediaType(result.getMediaType());
        if (mediaType == MovieMediaType.UNKNOWN || result.getId() == null) {
            return Optional.empty();
        }
        String title = firstNotBlank(result.getTitle(), result.getName());
        if (title.isBlank()) {
            return Optional.empty();
        }
        String originalTitle = firstNotBlank(result.getOriginalTitle(), result.getOriginalName());
        Integer year = extractYear(firstNotBlank(result.getReleaseDate(), result.getFirstAirDate()));
        return Optional.of(new MovieMetadata(
                nextSelectionId(),
                result.getId().toString(),
                mediaType,
                title,
                originalTitle,
                year,
                result.getVoteAverage(),
                result.getOverview(),
                tmdbClient.posterUrl(result.getPosterPath())
        ));
    }

    private Comparator<MovieMetadata> resultComparator(String query) {
        String normalizedQuery = normalizeForRank(query);
        return Comparator
                .comparingInt((MovieMetadata movieMetadata) -> exactTitleRank(movieMetadata, normalizedQuery))
                .thenComparing((MovieMetadata movieMetadata) -> movieMetadata.isTv(), Comparator.reverseOrder())
                .thenComparing((MovieMetadata movieMetadata) -> movieMetadata.posterUrl() != null && !movieMetadata.posterUrl().isBlank(), Comparator.reverseOrder())
                .thenComparing(movieMetadata -> movieMetadata.rating() == null ? 0.0 : movieMetadata.rating(), Comparator.reverseOrder())
                .thenComparing(movieMetadata -> movieMetadata.year() == null ? 0 : movieMetadata.year(), Comparator.reverseOrder());
    }

    private int exactTitleRank(MovieMetadata movieMetadata, String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return 2;
        }
        String title = normalizeForRank(movieMetadata.title());
        String originalTitle = normalizeForRank(movieMetadata.originalTitle());
        if (normalizedQuery.equals(title) || normalizedQuery.equals(originalTitle)) {
            return 0;
        }
        if (title.contains(normalizedQuery) || originalTitle.contains(normalizedQuery)) {
            return 1;
        }
        return 2;
    }

    private String normalizeForRank(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private MovieMediaType mediaType(String value) {
        if ("movie".equals(value)) {
            return MovieMediaType.MOVIE;
        }
        if ("tv".equals(value)) {
            return MovieMediaType.TV;
        }
        return MovieMediaType.UNKNOWN;
    }

    private Integer extractYear(String date) {
        if (date == null || date.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim().replaceAll("\\s+", " ");
    }

    private String firstNotBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String nextSelectionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private void cleanupExpiredEntries() {
        Instant now = Instant.now();
        queryCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        selectionCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record CacheEntry(List<MovieMetadata> results, Instant expiresAt) {
    }

    private record MovieEntry(MovieMetadata movieMetadata, Instant expiresAt) {
    }
}
