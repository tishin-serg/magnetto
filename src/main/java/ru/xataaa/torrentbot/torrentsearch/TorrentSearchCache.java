package ru.xataaa.torrentbot.torrentsearch;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class TorrentSearchCache {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, SearchEntry> searches = new ConcurrentHashMap<>();

    public TorrentSearchResult store(TorrentSearchResult torrentSearchResult) {
        cleanupExpired();
        String selectionId = nextToken(8);
        TorrentSearchResult storedResult = new TorrentSearchResult(
                selectionId,
                torrentSearchResult.title(),
                torrentSearchResult.tracker(),
                torrentSearchResult.magnetUri(),
                torrentSearchResult.link(),
                torrentSearchResult.details(),
                torrentSearchResult.sizeBytes(),
                torrentSearchResult.seeders(),
                torrentSearchResult.peers(),
                torrentSearchResult.publishDate(),
                torrentSearchResult.selectedSeasonNumber(),
                torrentSearchResult.selectedEpisodeNumbers()
        );
        entries.put(selectionId, new CacheEntry(storedResult, Instant.now().plus(TTL)));
        return storedResult;
    }

    public Optional<TorrentSearchResult> find(String selectionId) {
        cleanupExpired();
        CacheEntry cacheEntry = entries.get(selectionId);
        if (cacheEntry == null || cacheEntry.expiresAt().isBefore(Instant.now())) {
            entries.remove(selectionId);
            return Optional.empty();
        }
        return Optional.of(cacheEntry.torrentSearchResult());
    }

    public String storeSearch(String query, List<TorrentSearchResult> results) {
        cleanupExpired();
        String searchId = nextToken(6);
        searches.put(searchId, new SearchEntry(query, results, Instant.now().plus(TTL)));
        return searchId;
    }

    public Optional<SearchEntry> findSearch(String searchId) {
        cleanupExpired();
        SearchEntry searchEntry = searches.get(searchId);
        if (searchEntry == null || searchEntry.expiresAt().isBefore(Instant.now())) {
            searches.remove(searchId);
            return Optional.empty();
        }
        return Optional.of(searchEntry);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        searches.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String nextToken(int length) {
        byte[] tokenBytes = new byte[length];
        secureRandom.nextBytes(tokenBytes);
        return HexFormat.of().formatHex(tokenBytes);
    }

    private record CacheEntry(TorrentSearchResult torrentSearchResult, Instant expiresAt) {
    }

    public record SearchEntry(String query, List<TorrentSearchResult> results, Instant expiresAt) {
    }
}
