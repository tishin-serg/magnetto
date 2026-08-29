package ru.xataaa.torrentbot.media;

import java.time.LocalDateTime;

public record MediaLibraryFile(
        String fileName,
        long sizeBytes,
        LocalDateTime modifiedAt
) {
}
