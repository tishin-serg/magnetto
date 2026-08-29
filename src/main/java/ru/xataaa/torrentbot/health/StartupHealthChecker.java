package ru.xataaa.torrentbot.health;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.config.QbittorrentProperties;
import ru.xataaa.torrentbot.torrentsearch.JacredClient;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentClient;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupHealthChecker implements ApplicationRunner {

    private final QbittorrentClient qbittorrentClient;
    private final TelegramMessageService telegramMessageService;
    private final QbittorrentProperties qbittorrentProperties;
    private final JacredClient jacredClient;

    @Override
    public void run(ApplicationArguments args) {
        checkQbittorrent();
        checkTelegram();
        checkJacred();
        checkDownloadsDirectory();
    }

    private void checkQbittorrent() {
        try {
            qbittorrentClient.login();
            log.info("Startup health check passed: component=qbittorrent");
        } catch (RuntimeException runtimeException) {
            log.warn("Startup health check failed but application will continue: component=qbittorrent, error={}", runtimeException.getMessage());
        }
    }

    private void checkTelegram() {
        try {
            telegramMessageService.getMe();
            log.info("Startup health check passed: component=telegram");
        } catch (RuntimeException runtimeException) {
            log.warn("Startup health check failed but application will continue: component=telegram, error={}", runtimeException.getMessage());
        }
    }

    private void checkJacred() {
        try {
            if (jacredClient.healthCheck()) {
                log.info("Startup health check passed: component=jacred");
            }
        } catch (RuntimeException runtimeException) {
            log.warn("Startup health check failed but application will continue: component=jacred, error={}", runtimeException.getMessage());
        }
    }

    private void checkDownloadsDirectory() {
        Path downloadsPath = Path.of(qbittorrentProperties.downloadPath());
        if (Files.exists(downloadsPath) && Files.isDirectory(downloadsPath) && Files.isReadable(downloadsPath) && Files.isWritable(downloadsPath)) {
            log.info("Startup health check passed: component=downloads, path={}", downloadsPath);
            return;
        }
        log.warn("Startup health check failed but application will continue: component=downloads, path={}", downloadsPath);
    }
}
