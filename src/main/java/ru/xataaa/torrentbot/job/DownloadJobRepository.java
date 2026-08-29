package ru.xataaa.torrentbot.job;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ru.xataaa.torrentbot.common.ErrorCode;

public interface DownloadJobRepository {
    void save(DownloadJob downloadJob);
    Optional<DownloadJob> findById(UUID id);
    List<DownloadJob> findByStatuses(Set<DownloadJobStatus> statuses, int limit);
    List<DownloadJob> findRetryable(LocalDateTime now, int limit);
    List<DownloadJob> findRecent(int limit);
    List<DownloadJob> findQueued(int limit);
    boolean hasActiveJob();
    Optional<DownloadJob> findNextQueued();
    void updateStatus(UUID jobId, DownloadJobStatus status);
    void updateStatusWithError(UUID jobId, DownloadJobStatus status, ErrorCode errorCode, String errorMessage);
    void updateTargetStatus(UUID jobId, TargetStatus targetStatus, String targetErrorMessage);
    void pauseWithResumeStatus(UUID jobId, DownloadJobStatus resumeStatus);
    void scheduleRetry(UUID jobId, DownloadJobStatus status, ErrorCode errorCode, String errorMessage, int retryCount, LocalDateTime nextRetryAt);
    void updateTorrentIdentity(UUID jobId, String torrentHash, String torrentName);
    void updateLastReportedProgress(UUID jobId, int progressPercent);
    void updateStatusMessageId(UUID jobId, Long messageId);
    void markCompleted(UUID jobId, LocalDateTime completedAt);
    void markFailed(UUID jobId, ErrorCode errorCode, String errorMessage, LocalDateTime failedAt);
}
