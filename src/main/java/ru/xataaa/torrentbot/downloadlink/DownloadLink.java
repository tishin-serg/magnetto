package ru.xataaa.torrentbot.downloadlink;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DownloadLink {
    private UUID id;
    private UUID jobId;
    private UUID fileId;
    private String token;
    private Long chatId;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private long fileSizeBytes;
    private DownloadLinkStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime usedAt;
    private LocalDateTime deletedAt;
}
