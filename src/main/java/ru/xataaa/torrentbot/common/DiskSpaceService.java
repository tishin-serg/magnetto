package ru.xataaa.torrentbot.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;

@Service
@RequiredArgsConstructor
public class DiskSpaceService {

    private final QbittorrentTorrentService qbittorrentTorrentService;

    public DiskSpaceInfo downloadStorageInfo() {
        return downloadStorageInfo(DownloadTarget.VPS);
    }

    public DiskSpaceInfo downloadStorageInfo(DownloadTarget downloadTarget) {
        requireDiskBackedTarget(downloadTarget);
        return new DiskSpaceInfo(-1L, qbittorrentTorrentService.getFreeDiskSpace(downloadTarget));
    }

    public boolean hasEnoughSpace(long requiredBytes) {
        return hasEnoughSpace(DownloadTarget.VPS, requiredBytes);
    }

    public boolean hasEnoughSpace(DownloadTarget downloadTarget, long requiredBytes) {
        if (requiredBytes <= 0) {
            return true;
        }
        return downloadStorageInfo(downloadTarget).usableBytes() >= requiredBytes;
    }

    private void requireDiskBackedTarget(DownloadTarget downloadTarget) {
        if (downloadTarget == null || downloadTarget.isS3()) {
            throw new IllegalArgumentException("Disk space is not checked for target " + downloadTarget);
        }
    }

    public record DiskSpaceInfo(long totalBytes, long usableBytes) {
    }
}
