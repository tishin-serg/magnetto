package ru.xataaa.torrentbot.downloadlink;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DownloadLinkRepository {
    void save(DownloadLink downloadLink);
    Optional<DownloadLink> findByToken(String token);
    Optional<DownloadLink> findActiveByFileId(UUID fileId);
    boolean existsActiveByJobId(UUID jobId);
    List<DownloadLink> findExpiredActiveLinks(LocalDateTime now);
    List<DownloadLink> findByStatus(DownloadLinkStatus status);
    void updateStatus(UUID id, DownloadLinkStatus status, LocalDateTime now);
    void markUsed(UUID id, LocalDateTime usedAt);
}
