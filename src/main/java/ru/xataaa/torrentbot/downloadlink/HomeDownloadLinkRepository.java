package ru.xataaa.torrentbot.downloadlink;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeDownloadLinkRepository {
    void save(HomeDownloadLink homeDownloadLink);

    Optional<HomeDownloadLink> findByToken(String token);

    List<HomeDownloadLink> findExpiredActiveLinks(LocalDateTime now);

    void updateStatus(UUID id, DownloadLinkStatus status);

    void markUsed(UUID id, LocalDateTime usedAt);
}
