package ru.xataaa.torrentbot.telegram;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class DownloadTargetSelectionCache {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final ConcurrentMap<String, PendingDownload> pendingDownloads = new ConcurrentHashMap<>();

    public String put(Long chatId, String magnetUrl, long expectedSizeBytes, String title) {
        cleanupExpired();
        String selectionId = UUID.randomUUID().toString();
        pendingDownloads.put(selectionId, new PendingDownload(chatId, magnetUrl, expectedSizeBytes, title, Instant.now().plus(TTL)));
        return selectionId;
    }

    public Optional<PendingDownload> find(String selectionId, Long chatId) {
        PendingDownload pendingDownload = pendingDownloads.get(selectionId);
        if (pendingDownload == null || pendingDownload.expiresAt().isBefore(Instant.now()) || !pendingDownload.chatId().equals(chatId)) {
            pendingDownloads.remove(selectionId);
            return Optional.empty();
        }
        return Optional.of(pendingDownload);
    }

    public void remove(String selectionId) {
        pendingDownloads.remove(selectionId);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        pendingDownloads.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record PendingDownload(Long chatId, String magnetUrl, long expectedSizeBytes, String title, Instant expiresAt) {
    }
}
