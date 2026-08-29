package ru.xataaa.torrentbot.common;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.config.QbittorrentProperties;

@Service
@RequiredArgsConstructor
public class DiskSpaceService {

    private final QbittorrentProperties qbittorrentProperties;

    public DiskSpaceInfo downloadStorageInfo() {
        try {
            Path downloadPath = Path.of(qbittorrentProperties.downloadPath());
            Files.createDirectories(downloadPath);
            FileStore fileStore = Files.getFileStore(downloadPath);
            return new DiskSpaceInfo(fileStore.getTotalSpace(), fileStore.getUsableSpace());
        } catch (IOException ioException) {
            throw new IllegalStateException("Cannot read download storage space", ioException);
        }
    }

    public boolean hasEnoughSpace(long requiredBytes) {
        if (requiredBytes <= 0) {
            return true;
        }
        return downloadStorageInfo().usableBytes() >= requiredBytes;
    }

    public record DiskSpaceInfo(long totalBytes, long usableBytes) {
    }
}
