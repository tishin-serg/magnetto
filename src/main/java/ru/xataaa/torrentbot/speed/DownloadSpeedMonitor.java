package ru.xataaa.torrentbot.speed;

import java.time.LocalDateTime;
import java.util.UUID;

public record DownloadSpeedMonitor(UUID jobId, LocalDateTime startedAt, LocalDateTime endsAt,
                                   LocalDateTime nextCheckAt, Long lastSpeedBytesPerSecond,
                                   int consecutiveLowChecks, boolean alertSent,
                                   LocalDateTime stoppedAt, UUID replacementJobId) {
}
