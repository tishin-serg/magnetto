package ru.xataaa.torrentbot.file;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import ru.xataaa.torrentbot.common.ErrorCode;

@Data
@Builder
public class DownloadFile {
    private UUID id;
    private UUID jobId;
    private String fileName;
    private String relativePath;
    private Integer torrentFileIndex;
    private long sizeBytes;
    private DownloadFileStatus status;
    private Long telegramMessageId;
    private String s3ObjectKey;
    private int uploadAttempts;
    private int cleanupAttempts;
    private ErrorCode errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
