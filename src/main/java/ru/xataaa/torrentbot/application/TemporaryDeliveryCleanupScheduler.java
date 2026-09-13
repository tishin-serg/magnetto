package ru.xataaa.torrentbot.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;

@Component
@RequiredArgsConstructor
public class TemporaryDeliveryCleanupScheduler {
    private final TemporaryDeliveryArtifactRepository artifacts;
    private final S3MediaLibraryService s3;

    @Scheduled(fixedDelayString = "${SHORTCUT_API_TEMPORARY_CLEANUP_INTERVAL_MS:1800000}")
    public void cleanup() {
        for (TemporaryDeliveryArtifactRepository.Artifact artifact : artifacts.findExpired(LocalDateTime.now())) {
            try {
                if ("S3".equals(artifact.storageType())) s3.deleteFile(artifact.key());
                artifacts.markCleaned(artifact.id(), LocalDateTime.now());
            } catch (RuntimeException ignored) {
                // Keep the row pending so a transient S3 failure is retried later.
            }
        }
    }
}
