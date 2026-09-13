package ru.xataaa.torrentbot.job;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.application.DeliveryTarget;
import ru.xataaa.torrentbot.application.ExecutionTarget;

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
    private UUID userId;
    private ExecutionTarget executionTarget;
    private DeliveryTarget deliveryTarget;
    private Integer seasonNumber;
    private String episodeNumbers;
    private TargetStatus targetStatus;
    private String targetErrorMessage;
    private ErrorCode errorCode;
    private String errorMessage;
    private int retryCount;
    private LocalDateTime nextRetryAt;
    private boolean deleteAfterUpload;
    private int lastReportedProgressPercent;
    private Long statusMessageId;
    private long minDownloadSpeedBytesPerSecond;
    private boolean autoReplaceSlowDownload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
}
