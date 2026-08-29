package ru.xataaa.torrentbot.torrentsearch;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.SafeLog;
import ru.xataaa.torrentbot.movie.MovieMetadata;

@Service
@RequiredArgsConstructor
@Slf4j
public class TorrentAvailabilityService {

    private static final long TTL_SECONDS = 30 * 60L;

    private final TorrentSearchService torrentSearchService;
    private final TorrentTitleParser torrentTitleParser;
    private final Map<String, CachedCatalog> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<TorrentAvailabilityCatalog>> inFlightCatalogs = new ConcurrentHashMap<>();

    public TorrentAvailabilityCatalog catalog(MovieMetadata movieMetadata) {
        String cacheKey = movieMetadata.tmdbId() == null ? movieMetadata.selectionId() : movieMetadata.tmdbId();
        String cacheKeyHash = SafeLog.sha256Short(cacheKey);
        CachedCatalog cachedCatalog = cache.get(cacheKey);
        if (cachedCatalog != null && cachedCatalog.expiresAt().isAfter(Instant.now())) {
            log.info("availability_catalog_cache_hit: keyHash={}, itemCount={}", cacheKeyHash, cachedCatalog.catalog().items().size());
            return cachedCatalog.catalog();
        }
        log.info("availability_catalog_cache_miss: keyHash={}, title={}", cacheKeyHash, SafeLog.preview(movieMetadata.title(), 40));
        CompletableFuture<TorrentAvailabilityCatalog> currentCatalog = new CompletableFuture<>();
        CompletableFuture<TorrentAvailabilityCatalog> existingCatalog = inFlightCatalogs.putIfAbsent(cacheKey, currentCatalog);
        if (existingCatalog != null) {
            log.info("availability_catalog_singleflight_wait: keyHash={}", cacheKeyHash);
            try {
                return existingCatalog.join();
            } catch (CompletionException completionException) {
                Throwable cause = completionException.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw completionException;
            }
        }
        try {
            TorrentAvailabilityCatalog catalog = loadCatalog(movieMetadata);
            cache.put(cacheKey, new CachedCatalog(catalog, Instant.now().plusSeconds(TTL_SECONDS)));
            log.info("availability_catalog_cache_store: keyHash={}, itemCount={}", cacheKeyHash, catalog.items().size());
            currentCatalog.complete(catalog);
            return catalog;
        } catch (RuntimeException runtimeException) {
            currentCatalog.completeExceptionally(runtimeException);
            throw runtimeException;
        } finally {
            inFlightCatalogs.remove(cacheKey, currentCatalog);
        }
    }

    private TorrentAvailabilityCatalog loadCatalog(MovieMetadata movieMetadata) {
        List<TorrentSearchResult> results = torrentSearchService.search(movieMetadata, TorrentSearchFilters.any());
        List<TorrentAvailabilityItem> items = deduplicate(results).stream()
                .map(result -> {
                    TorrentTitleParser.ParsedTorrentTitle parsed = torrentTitleParser.parse(result);
                    if (movieMetadata.isTv() && parsed.seasonRange() == null) {
                        return null;
                    }
                    return new TorrentAvailabilityItem(
                            result,
                            movieMetadata.isTv() ? parsed.seasonRange() : null,
                            parsed.episodeRange(),
                            movieMetadata.isTv() ? parsed.releaseType() : ReleaseType.MOVIE,
                            parsed.quality(),
                            parsed.voice()
                    );
                })
                .filter(item -> item != null)
                .toList();
        return new TorrentAvailabilityCatalog(movieMetadata, items);
    }

    private List<TorrentSearchResult> deduplicate(List<TorrentSearchResult> results) {
        Map<String, TorrentSearchResult> deduplicated = new LinkedHashMap<>();
        for (TorrentSearchResult result : results) {
            String key = firstNotBlank(result.magnetUri(), result.link(), normalizedTitle(result.title()));
            deduplicated.putIfAbsent(key, result);
        }
        return List.copyOf(deduplicated.values());
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim().toLowerCase();
            }
        }
        return "";
    }

    private String normalizedTitle(String title) {
        return title == null ? "" : title.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private record CachedCatalog(TorrentAvailabilityCatalog catalog, Instant expiresAt) {
    }
}
