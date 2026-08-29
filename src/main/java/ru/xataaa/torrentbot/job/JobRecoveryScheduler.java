package ru.xataaa.torrentbot.job;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.file.DownloadFileRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobRecoveryScheduler {

    private final DownloadJobRepository downloadJobRepository;
    private final DownloadFileRepository downloadFileRepository;
    private final DownloadOrchestrator downloadOrchestrator;
    private final TimeProvider timeProvider;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${app.recovery-poll-interval-ms}")
    public void recoverJobs() {
        LocalDateTime now = timeProvider.now();
        List<DownloadJob> retryableJobs = downloadJobRepository.findRetryable(now, 50);
        for (DownloadJob retryableJob : retryableJobs) {
            try {
                markStaleUploadsIfNeeded(retryableJob, now);
                downloadOrchestrator.processJob(retryableJob.getId());
            } catch (RuntimeException runtimeException) {
                log.warn("Recovery failed: jobId={}, error={}", retryableJob.getId(), runtimeException.getMessage());
            }
        }
        downloadOrchestrator.startQueuedJobs();
    }

    private void markStaleUploadsIfNeeded(DownloadJob downloadJob, LocalDateTime now) {
        if (downloadJob.getResumeStatus() == DownloadJobStatus.UPLOADING_TO_TELEGRAM) {
            LocalDateTime staleBefore = now.minusHours(appProperties.uploadTimeoutHours());
            downloadFileRepository.markStaleUploads(downloadJob.getId(), staleBefore);
        }
    }
}
