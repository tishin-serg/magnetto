package ru.xataaa.torrentbot.qbittorrent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.config.QbittorrentProperties;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentFile;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentInfo;
import ru.xataaa.torrentbot.retry.RetryExecutor;

@Service
@RequiredArgsConstructor
public class QbittorrentTorrentService {

    private final QbittorrentClient qbittorrentClient;
    private final QbittorrentProperties qbittorrentProperties;
    private final RetryExecutor retryExecutor;

    public void addMagnet(UUID jobId, String magnetUrl) {
        addMagnet(DownloadTarget.VPS, jobId, magnetUrl);
    }

    public void addMagnet(DownloadTarget downloadTarget, UUID jobId, String magnetUrl) {
        QbittorrentProperties.TargetProperties targetProperties = qbittorrentProperties.target(downloadTarget);
        retryExecutor.executeVoid("qbittorrent.addMagnet", () ->
                qbittorrentClient.addMagnet(downloadTarget, magnetUrl, targetProperties.downloadPath(), jobTag(jobId))
        );
    }

    public Optional<QbittorrentTorrentInfo> getTorrentInfoByJobTag(UUID jobId) {
        return getTorrentInfoByJobTag(DownloadTarget.VPS, jobId);
    }

    public Optional<QbittorrentTorrentInfo> getTorrentInfoByJobTag(DownloadTarget downloadTarget, UUID jobId) {
        String tag = jobTag(jobId);
        return retryExecutor.execute("qbittorrent.getTorrentByTag." + downloadTarget.name().toLowerCase(), () -> qbittorrentClient.getTorrentsInfo(downloadTarget).stream()
                .filter(torrent -> torrent.getTags() != null && List.of(torrent.getTags().split(",")).stream()
                        .map(String::trim)
                        .anyMatch(tag::equals))
                .findFirst());
    }

    public Optional<QbittorrentTorrentInfo> getTorrentInfoByHash(String hash) {
        return getTorrentInfoByHash(DownloadTarget.VPS, hash);
    }

    public Optional<QbittorrentTorrentInfo> getTorrentInfoByHash(DownloadTarget downloadTarget, String hash) {
        return retryExecutor.execute("qbittorrent.getTorrentByHash." + downloadTarget.name().toLowerCase(), () -> qbittorrentClient.getTorrentsInfo(downloadTarget).stream()
                .filter(torrent -> hash.equalsIgnoreCase(torrent.getHash()))
                .findFirst());
    }

    public List<QbittorrentTorrentFile> getTorrentFiles(String hash) {
        return getTorrentFiles(DownloadTarget.VPS, hash);
    }

    public List<QbittorrentTorrentFile> getTorrentFiles(DownloadTarget downloadTarget, String hash) {
        return retryExecutor.execute("qbittorrent.getTorrentFiles." + downloadTarget.name().toLowerCase(), () -> qbittorrentClient.getTorrentFiles(downloadTarget, hash));
    }

    public void deleteTorrent(String hash, boolean deleteFiles) {
        deleteTorrent(DownloadTarget.VPS, hash, deleteFiles);
    }

    public void deleteTorrent(DownloadTarget downloadTarget, String hash, boolean deleteFiles) {
        retryExecutor.executeVoid("qbittorrent.deleteTorrent." + downloadTarget.name().toLowerCase(), () -> qbittorrentClient.deleteTorrent(downloadTarget, hash, deleteFiles));
    }

    public void setFilePriority(String hash, List<Integer> fileIndexes, int priority) {
        setFilePriority(DownloadTarget.VPS, hash, fileIndexes, priority);
    }

    public void setFilePriority(DownloadTarget downloadTarget, String hash, List<Integer> fileIndexes, int priority) {
        retryExecutor.executeVoid("qbittorrent.setFilePriority." + downloadTarget.name().toLowerCase(), () -> qbittorrentClient.setFilePriority(downloadTarget, hash, fileIndexes, priority));
    }

    public void pauseTorrent(String hash) {
        pauseTorrent(DownloadTarget.VPS, hash);
    }

    public void pauseTorrent(DownloadTarget downloadTarget, String hash) {
        retryExecutor.executeVoid("qbittorrent.pauseTorrent." + downloadTarget.name().toLowerCase(), () -> qbittorrentClient.pauseTorrent(downloadTarget, hash));
    }

    public void resumeTorrent(String hash) {
        resumeTorrent(DownloadTarget.VPS, hash);
    }

    public void resumeTorrent(DownloadTarget downloadTarget, String hash) {
        retryExecutor.executeVoid("qbittorrent.resumeTorrent." + downloadTarget.name().toLowerCase(), () -> qbittorrentClient.resumeTorrent(downloadTarget, hash));
    }

    public String jobTag(UUID jobId) {
        return "job:" + jobId;
    }
}
