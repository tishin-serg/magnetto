package ru.xataaa.torrentbot.media;

import java.time.LocalDateTime;

public record HomeMediaLibraryItem(
        HomeMediaLibraryItemType type,
        String displayName,
        String folderPath,
        String folderKey,
        HomeMediaLibraryFile file,
        int fileCount,
        long totalSizeBytes,
        LocalDateTime latestModifiedAt
) {
    public boolean isFolder() {
        return type == HomeMediaLibraryItemType.FOLDER;
    }

    public boolean isDirectFile() {
        return type == HomeMediaLibraryItemType.DIRECT_FILE;
    }
}
