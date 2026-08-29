package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.library")
public record MediaLibraryProperties(
        boolean enabled,
        String path,
        String publicWebdavUrl,
        MoveStrategy moveStrategy,
        MoveStrategy fallbackMoveStrategy,
        boolean autoDeleteEnabled,
        int retentionDays
) {
    public enum MoveStrategy {
        HARDLINK,
        COPY,
        MOVE
    }
}
