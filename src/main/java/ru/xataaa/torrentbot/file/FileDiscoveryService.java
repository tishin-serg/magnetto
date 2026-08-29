package ru.xataaa.torrentbot.file;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentFile;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDiscoveryService {

    private final QbittorrentTorrentService qbittorrentTorrentService;
    private final DownloadFileRepository downloadFileRepository;
    private final FileFilterService fileFilterService;
    private final TimeProvider timeProvider;

    public void discoverFiles(UUID jobId, String torrentHash) {
        discoverFiles(DownloadTarget.VPS, jobId, torrentHash);
    }

    public void discoverFiles(DownloadTarget downloadTarget, UUID jobId, String torrentHash) {
        List<QbittorrentTorrentFile> torrentFiles = qbittorrentTorrentService.getTorrentFiles(downloadTarget, torrentHash);
        if (torrentFiles.isEmpty()) {
            throw new NonRetryableOperationException(ErrorCode.QBITTORRENT_FILES_NOT_FOUND, "Downloaded files were not found");
        }
        LocalDateTime now = timeProvider.now();
        for (int fileIndex = 0; fileIndex < torrentFiles.size(); fileIndex++) {
            QbittorrentTorrentFile torrentFile = torrentFiles.get(fileIndex);
            String fileName = extractFileName(torrentFile.getName());
            DownloadFileStatus status = fileFilterService.classify(fileName, torrentFile.getSize());
            DownloadFile downloadFile = DownloadFile.builder()
                    .id(UUID.randomUUID())
                    .jobId(jobId)
                    .fileName(fileName)
                    .relativePath(torrentFile.getName())
                    .torrentFileIndex(fileIndex)
                    .sizeBytes(torrentFile.getSize())
                    .status(status)
                    .uploadAttempts(0)
                    .cleanupAttempts(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            downloadFileRepository.saveIfAbsent(downloadFile);
            log.info("Discovered file: jobId={}, fileName={}, sizeBytes={}, status={}", jobId, fileName, torrentFile.getSize(), status);
        }
    }

    private String extractFileName(String relativePath) {
        int slashIndex = Math.max(relativePath.lastIndexOf('/'), relativePath.lastIndexOf('\\'));
        if (slashIndex < 0) {
            return relativePath;
        }
        return relativePath.substring(slashIndex + 1);
    }
}
