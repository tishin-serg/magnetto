package ru.xataaa.torrentbot.downloadlink;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class InMemoryDownloadLinkRepository implements DownloadLinkRepository {

    private final List<DownloadLink> links = new ArrayList<>();

    @Override
    public void save(DownloadLink downloadLink) {
        links.add(downloadLink);
    }

    @Override
    public Optional<DownloadLink> findByToken(String token) {
        return links.stream().filter(link -> link.getToken().equals(token)).findFirst();
    }

    @Override
    public Optional<DownloadLink> findActiveByFileId(UUID fileId) {
        return links.stream()
                .filter(link -> link.getFileId().equals(fileId))
                .filter(link -> link.getStatus() == DownloadLinkStatus.ACTIVE)
                .findFirst();
    }

    @Override
    public boolean existsActiveByJobId(UUID jobId) {
        return links.stream()
                .anyMatch(link -> link.getJobId().equals(jobId) && link.getStatus() == DownloadLinkStatus.ACTIVE);
    }

    @Override
    public List<DownloadLink> findExpiredActiveLinks(LocalDateTime now) {
        return links.stream()
                .filter(link -> link.getStatus() == DownloadLinkStatus.ACTIVE)
                .filter(link -> !link.getExpiresAt().isAfter(now))
                .toList();
    }

    @Override
    public List<DownloadLink> findByStatus(DownloadLinkStatus status) {
        return links.stream().filter(link -> link.getStatus() == status).toList();
    }

    @Override
    public void updateStatus(UUID id, DownloadLinkStatus status, LocalDateTime now) {
        findById(id).ifPresent(link -> {
            link.setStatus(status);
            if (status == DownloadLinkStatus.DELETED) {
                link.setDeletedAt(now);
            }
        });
    }

    @Override
    public void markUsed(UUID id, LocalDateTime usedAt) {
        findById(id).ifPresent(link -> link.setUsedAt(usedAt));
    }

    private Optional<DownloadLink> findById(UUID id) {
        return links.stream().filter(link -> link.getId().equals(id)).findFirst();
    }
}
