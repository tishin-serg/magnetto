package ru.xataaa.torrentbot.job;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TorrentProgressScheduler {

    private static final Set<DownloadJobStatus> ACTIVE_STATUSES = Set.of(
            DownloadJobStatus.CREATED,
            DownloadJobStatus.ADDING_TO_QBITTORRENT,
            DownloadJobStatus.ADDED_TO_QBITTORRENT,
            DownloadJobStatus.WAITING_METADATA,
            DownloadJobStatus.DOWNLOADING,
            DownloadJobStatus.DOWNLOAD_COMPLETED,
            DownloadJobStatus.DISCOVERING_FILES,
            DownloadJobStatus.DELIVERY_PENDING,
            DownloadJobStatus.UPLOADING_TO_TELEGRAM,
            DownloadJobStatus.DELIVERY_COMPLETED,
            DownloadJobStatus.CLEANUP_PENDING,
            DownloadJobStatus.CLEANING_UP,
            DownloadJobStatus.CLEANUP_COMPLETED
    );

    private final DownloadJobRepository downloadJobRepository;
    private final DownloadOrchestrator downloadOrchestrator;

    @Scheduled(fixedDelayString = "${app.progress-poll-interval-ms}")
    public void processActiveJobs() {
        List<DownloadJob> activeJobs = downloadJobRepository.findByStatuses(ACTIVE_STATUSES, 50);
        for (DownloadJob activeJob : activeJobs) {
            try {
                downloadOrchestrator.processJob(activeJob.getId());
            } catch (RuntimeException runtimeException) {
                log.warn("Active job processing failed: jobId={}, error={}", activeJob.getId(), runtimeException.getMessage());
            }
        }
    }
}
