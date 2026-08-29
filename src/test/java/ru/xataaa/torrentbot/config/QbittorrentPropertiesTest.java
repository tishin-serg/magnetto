package ru.xataaa.torrentbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.job.DownloadTarget;

class QbittorrentPropertiesTest {

    @Test
    void shouldUseLegacyPropertiesForVpsWhenNestedTargetIsMissing() {
        QbittorrentProperties properties = new QbittorrentProperties(
                "http://vps:8080",
                "admin",
                "password",
                "/downloads",
                1000,
                2000,
                null,
                null
        );

        assertThat(properties.target(DownloadTarget.VPS).baseUrl()).isEqualTo("http://vps:8080");
        assertThat(properties.target(DownloadTarget.VPS).downloadPath()).isEqualTo("/downloads");
    }

    @Test
    void shouldReturnHomeTargetProperties() {
        QbittorrentProperties.TargetProperties home = new QbittorrentProperties.TargetProperties(
                "http://home:8080",
                "home-user",
                "home-password",
                "/media/movies",
                3000,
                4000
        );
        QbittorrentProperties properties = new QbittorrentProperties(
                "http://vps:8080",
                "admin",
                "password",
                "/downloads",
                1000,
                2000,
                null,
                home
        );

        assertThat(properties.target(DownloadTarget.HOME_PC)).isEqualTo(home);
    }
}
