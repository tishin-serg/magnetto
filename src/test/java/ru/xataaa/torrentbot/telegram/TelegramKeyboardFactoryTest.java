package ru.xataaa.torrentbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.media.S3MediaLibraryFile;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;

class TelegramKeyboardFactoryTest {

    private final TelegramKeyboardFactory factory = new TelegramKeyboardFactory();

    @Test
    void shouldIncludeS3LibraryInMainMenuAndCleanup() {
        assertThat(factory.mainMenuKeyboard()).contains("S3 медиатека", "menu:library:s3");
        assertThat(factory.cleanupConfirmKeyboard()).contains("Очистить S3 медиатеку", "media:cleanup:confirm:s3");
    }

    @Test
    void shouldBuildS3LibraryPagingAndFileDetailCallbacks() {
        S3MediaLibraryFile file = new S3MediaLibraryFile("movie.mkv", "media-library/movie.mkv", 1024L, LocalDateTime.now());
        S3MediaLibraryService s3MediaLibraryService = mock(S3MediaLibraryService.class);
        when(s3MediaLibraryService.fileKey(file)).thenReturn("file-key");

        String keyboard = factory.s3MediaLibraryKeyboard(List.of(file), s3MediaLibraryService, 0, 10);

        assertThat(keyboard).contains("s3:file:file-key", "menu:library:s3:page:0", "Назад в меню");
    }
}
