package ru.xataaa.torrentbot.job;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentInfo;

/** Immutable observations from the existing polling loop: opening a menu never blocks on qBittorrent. */
@Component
public class DownloadTelemetry {
    public record Snapshot(double progress, long size, long speed, long eta, String state, Instant observedAt) {}
    private final ConcurrentHashMap<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    public void record(UUID id, QbittorrentTorrentInfo info) {
        Instant now = Instant.now();
        snapshots.entrySet().removeIf(entry -> entry.getValue().observedAt().isBefore(now.minusSeconds(86400)));
        if (snapshots.size() >= 1000 && !snapshots.containsKey(id)) snapshots.clear();
        snapshots.put(id, new Snapshot(Math.max(0, Math.min(1, info.getProgress())),
                Math.max(0, info.getSize()), Math.max(0, info.getDownloadSpeed()), info.getEta(), info.getState(), now));
    }

    public Snapshot get(UUID id) { return snapshots.get(id); }
}
