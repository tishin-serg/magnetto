package ru.xataaa.torrentbot.downloadlink;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.xataaa.torrentbot.config.DownloadsProperties;
import ru.xataaa.torrentbot.config.TelegramProperties;
import ru.xataaa.torrentbot.file.DownloadFile;
import ru.xataaa.torrentbot.file.DownloadFileStatus;

class DownloadLinkServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldCreateLinkWithExpiresAtAndSecureToken() throws Exception {
        InMemoryDownloadLinkRepository repository = new InMemoryDownloadLinkRepository();
        FixedTimeProvider timeProvider = new FixedTimeProvider(LocalDateTime.of(2026, 6, 8, 10, 0));
        DownloadLinkService service = service(repository, timeProvider);
        Path sourceFile = tempDirectory.resolve("movie.mkv");
        Files.writeString(sourceFile, "video");

        DownloadLink link = service.createDownloadLink(123L, file(sourceFile.getFileName().toString(), 5));

        assertThat(link.getExpiresAt()).isEqualTo(timeProvider.now().plusHours(24));
        assertThat(link.getToken()).hasSizeGreaterThan(30);
        assertThat(Files.exists(Path.of(link.getFilePath()))).isTrue();
    }

    @Test
    void shouldCreateDifferentTokensForDifferentFiles() throws Exception {
        InMemoryDownloadLinkRepository repository = new InMemoryDownloadLinkRepository();
        FixedTimeProvider timeProvider = new FixedTimeProvider(LocalDateTime.of(2026, 6, 8, 10, 0));
        DownloadLinkService service = service(repository, timeProvider);
        Path firstFile = tempDirectory.resolve("first.mkv");
        Path secondFile = tempDirectory.resolve("second.mkv");
        Files.writeString(firstFile, "first");
        Files.writeString(secondFile, "second");

        DownloadLink firstLink = service.createDownloadLink(123L, file(firstFile.getFileName().toString(), 5));
        DownloadLink secondLink = service.createDownloadLink(123L, file(secondFile.getFileName().toString(), 6));

        assertThat(firstLink.getToken()).isNotEqualTo(secondLink.getToken());
    }

    @Test
    void shouldNotReturnExpiredOrDeletedLink() throws Exception {
        InMemoryDownloadLinkRepository repository = new InMemoryDownloadLinkRepository();
        FixedTimeProvider timeProvider = new FixedTimeProvider(LocalDateTime.of(2026, 6, 8, 10, 0));
        DownloadLinkService service = service(repository, timeProvider);
        Path sourceFile = tempDirectory.resolve("movie.mkv");
        Files.writeString(sourceFile, "video");
        DownloadLink link = service.createDownloadLink(123L, file(sourceFile.getFileName().toString(), 5));

        timeProvider.setNow(link.getExpiresAt().plusMinutes(1));
        assertThat(service.getValidDownloadLink(link.getToken())).isEmpty();

        repository.updateStatus(link.getId(), DownloadLinkStatus.DELETED, timeProvider.now());
        timeProvider.setNow(link.getExpiresAt().minusMinutes(1));
        assertThat(service.getValidDownloadLink(link.getToken())).isEmpty();
    }

    private DownloadLinkService service(DownloadLinkRepository repository, FixedTimeProvider timeProvider) {
        return new DownloadLinkService(
                repository,
                new DownloadsProperties("https://files.example.com", "/protected-downloads/", tempDirectory.toString(), 1800000),
                new TelegramProperties(
                        "token",
                        "bot",
                        "http://localhost:8081",
                        1000,
                        1000,
                        1000,
                        new TelegramProperties.FileProperties(100, 24, true)
                ),
                timeProvider
        );
    }

    private DownloadFile file(String relativePath, long sizeBytes) {
        LocalDateTime now = LocalDateTime.now();
        return DownloadFile.builder()
                .id(UUID.randomUUID())
                .jobId(UUID.randomUUID())
                .fileName(relativePath)
                .relativePath(relativePath)
                .sizeBytes(sizeBytes)
                .status(DownloadFileStatus.READY_TO_UPLOAD)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
