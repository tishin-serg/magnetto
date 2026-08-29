package ru.xataaa.torrentbot.downloadlink;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HomeDownloadLink {
    private UUID id;
    private String token;
    private Long chatId;
    private String fileName;
    private long fileSizeBytes;
    private DownloadLinkStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime usedAt;
}
