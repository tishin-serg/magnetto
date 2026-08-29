package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.xataaa.torrentbot.job.DownloadTarget;

@ConfigurationProperties(prefix = "qbittorrent")
public record QbittorrentProperties(
        String baseUrl,
        String username,
        String password,
        String downloadPath,
        int connectTimeoutMs,
        int requestTimeoutMs,
        TargetProperties vps,
        TargetProperties home
) {
    public QbittorrentProperties {
        TargetProperties legacyTargetProperties = new TargetProperties(
                baseUrl,
                username,
                password,
                downloadPath,
                connectTimeoutMs,
                requestTimeoutMs
        );
        if (vps == null) {
            vps = legacyTargetProperties;
        }
        if (home == null) {
            home = legacyTargetProperties;
        }
    }

    public TargetProperties target(DownloadTarget downloadTarget) {
        if (downloadTarget == DownloadTarget.HOME_PC) {
            return home;
        }
        return vps;
    }

    public record TargetProperties(
            String baseUrl,
            String username,
            String password,
            String downloadPath,
            int connectTimeoutMs,
            int requestTimeoutMs
    ) {
    }
}
