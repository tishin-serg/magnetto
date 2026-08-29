package ru.xataaa.torrentbot.media;

import java.time.LocalDateTime;

public record S3MediaLibraryFile(
        String fileName,
        String objectKey,
        long sizeBytes,
        LocalDateTime modifiedAt
) {
}
