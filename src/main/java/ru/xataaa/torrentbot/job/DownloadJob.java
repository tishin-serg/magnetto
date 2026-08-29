package ru.xataaa.torrentbot.job;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import ru.xataaa.torrentbot.common.ErrorCode;

@Data
@Builder
public class DownloadJob {
    private UUID id;
    private Long chatId;
    private String magnetUrl;
    private String magnetUrlHash;
    private String torrentHash;
    private String torrentName;
    private DownloadJobStatus status;
    private DownloadJobStatus resumeStatus;
    private DownloadTarget downloadTarget;
    private TargetStatus targetStatus;
    private String targetErrorMessage;
    private ErrorCode errorCode;
    private String errorMessage;
    private int retryCount;
    private LocalDateTime nextRetryAt;
    private boolean deleteAfterUpload;
    private int lastReportedProgressPercent;
    private Long statusMessageId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
}
