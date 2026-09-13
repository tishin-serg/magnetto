package ru.xataaa.torrentbot.application;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.file.*;
import ru.xataaa.torrentbot.job.*;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;

@Service
@RequiredArgsConstructor
public class DownloadControlService {
    private final DownloadJobRepository jobs;
    private final DownloadFileRepository files;
    private final DownloadFileSelectionService selectionService;
    private final QbittorrentTorrentService qb;
    private final DownloadOrchestrator orchestrator;
    public synchronized void pause(DownloadJob j) { if (j.getTorrentHash() != null) qb.pauseTorrent(j.getDownloadTarget(), j.getTorrentHash()); jobs.pauseWithResumeStatus(j.getId(), j.getStatus()); }
    public synchronized void resume(DownloadJob j) { if (j.getTorrentHash() != null) qb.resumeTorrent(j.getDownloadTarget(), j.getTorrentHash()); jobs.updateStatus(j.getId(), j.getResumeStatus() == null ? DownloadJobStatus.DOWNLOADING : j.getResumeStatus()); orchestrator.processJob(j.getId()); }
    public synchronized void select(DownloadJob j, List<UUID> selected) {
        List<DownloadFile> all = files.findByJobId(j.getId());
        var chosen = selectionService.confirm(j, selected);
        if (j.getTorrentHash() != null) { qb.setFilePriority(j.getDownloadTarget(), j.getTorrentHash(), all.stream().map(DownloadFile::getTorrentFileIndex).filter(java.util.Objects::nonNull).toList(), 0); qb.setFilePriority(j.getDownloadTarget(), j.getTorrentHash(), chosen.stream().map(DownloadFile::getTorrentFileIndex).filter(java.util.Objects::nonNull).toList(), 1); qb.resumeTorrent(j.getDownloadTarget(), j.getTorrentHash()); }
        jobs.updateStatus(j.getId(), DownloadJobStatus.DOWNLOADING); orchestrator.processJob(j.getId());
    }
}
