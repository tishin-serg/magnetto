package ru.xataaa.torrentbot.file;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final DownloadFileRepository downloadFileRepository;
    private final QbittorrentTorrentService qbittorrentTorrentService;

    public boolean canCleanup(UUID jobId) {
        List<DownloadFile> files = downloadFileRepository.findByJobId(jobId);
        for (DownloadFile file : files) {
            if (file.getStatus() == DownloadFileStatus.READY_TO_UPLOAD
                    || file.getStatus() == DownloadFileStatus.UPLOADING
                    || file.getStatus() == DownloadFileStatus.UPLOADING_TO_S3
                    || file.getStatus() == DownloadFileStatus.UNKNOWN_UPLOAD_RESULT
                    || file.getStatus() == DownloadFileStatus.UPLOAD_FAILED_RETRYABLE) {
                return false;
            }
            if (file.getStatus() != DownloadFileStatus.UPLOADED
                    && file.getStatus() != DownloadFileStatus.S3_UPLOADED
                    && file.getStatus() != DownloadFileStatus.DOWNLOAD_LINK_CREATED
                    && file.getStatus() != DownloadFileStatus.SKIPPED_TOO_LARGE
                    && file.getStatus() != DownloadFileStatus.SKIPPED_UNSUPPORTED
                    && file.getStatus() != DownloadFileStatus.SKIPPED_BY_USER
                    && file.getStatus() != DownloadFileStatus.DELETED) {
                return false;
            }
        }
        return true;
    }

    public void cleanup(UUID jobId, String torrentHash) {
        cleanup(DownloadTarget.VPS, jobId, torrentHash);
    }

    public void cleanup(DownloadTarget downloadTarget, UUID jobId, String torrentHash) {
        if (!canCleanup(jobId)) {
            throw new IllegalStateException("Cleanup is not allowed while files are pending");
        }
        qbittorrentTorrentService.deleteTorrent(downloadTarget, torrentHash, true);
        log.info("Cleanup completed: jobId={}, torrentHash={}, downloadTarget={}", jobId, torrentHash, downloadTarget);
    }
}
