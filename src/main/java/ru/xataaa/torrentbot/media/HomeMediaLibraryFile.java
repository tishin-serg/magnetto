package ru.xataaa.torrentbot.media;

import java.time.LocalDateTime;

public record HomeMediaLibraryFile(
        String fileName,
        String relativePath,
        long sizeBytes,
        LocalDateTime modifiedAt,
        String tailscaleUrl,
        String localWifiUrl
) {
}
