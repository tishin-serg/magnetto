package ru.xataaa.torrentbot.file;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.xataaa.torrentbot.common.ErrorCode;

public interface DownloadFileRepository {
    void save(DownloadFile downloadFile);
    void saveIfAbsent(DownloadFile downloadFile);
    List<DownloadFile> findByJobId(UUID jobId);
    List<DownloadFile> findByJobIdAndStatuses(UUID jobId, List<DownloadFileStatus> statuses);
    Optional<DownloadFile> findById(UUID id);
    void updateStatus(UUID fileId, DownloadFileStatus status);
    void updateStatusWithError(UUID fileId, DownloadFileStatus status, ErrorCode errorCode, String errorMessage);
    void markUploaded(UUID fileId, Long telegramMessageId);
    void markS3Uploaded(UUID fileId, String s3ObjectKey);
    void incrementUploadAttempt(UUID fileId, DownloadFileStatus status, ErrorCode errorCode, String errorMessage);
    void markStaleUploads(UUID jobId, LocalDateTime staleBefore);
}
