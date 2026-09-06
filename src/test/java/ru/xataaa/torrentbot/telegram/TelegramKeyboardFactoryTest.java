package ru.xataaa.torrentbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.media.S3MediaLibraryFile;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;
import ru.xataaa.torrentbot.regression.UiRegression;

@UiRegression
class TelegramKeyboardFactoryTest {

    private final TelegramKeyboardFactory factory = new TelegramKeyboardFactory();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeEveryMainMenuJourney() throws Exception {
        assertThat(callbacks(factory.mainMenuKeyboard())).containsExactlyInAnyOrder(
                "menu:search",
                "menu:tasks",
                "menu:settings",
                "menu:libraries",
                "menu:help"
        );
        assertThat(callbacks(factory.libraryMenuKeyboard())).contains(
                "menu:library:home",
                "menu:library:vps",
                "menu:library:s3",
                "menu:space",
                "media:cleanup:ask"
        );
    }

    @Test
    void shouldRequireExplicitCleanupTarget() throws Exception {
        assertThat(callbacks(factory.cleanupConfirmKeyboard())).containsExactlyInAnyOrder(
                "media:cleanup:confirm:local",
                "media:cleanup:confirm:home",
                "media:cleanup:confirm:s3",
                "menu:libraries"
        );
    }

    @Test
    void shouldExposeHomeAndS3FileActions() throws Exception {
        assertThat(callbacks(factory.homeFileKeyboard("home-key", "http://tailscale/file", "http://lan/file")))
                .containsExactlyInAnyOrder("home:link:home-key", "home:delete:ask:home-key", "menu:library:home");
        assertThat(callbacks(factory.homeFileDeleteConfirmKeyboard("home-key")))
                .containsExactlyInAnyOrder("home:delete:confirm:home-key", "home:delete:cancel:home-key", "menu:library:home");
        assertThat(callbacks(factory.s3FileKeyboard("s3-key")))
                .containsExactlyInAnyOrder("s3:download:s3-key", "s3:delete:ask:s3-key", "menu:library:s3");
        assertThat(callbacks(factory.s3FileDeleteConfirmKeyboard("s3-key")))
                .containsExactlyInAnyOrder("s3:delete:confirm:s3-key", "s3:delete:cancel:s3-key", "menu:library:s3");
    }

    @Test
    void shouldBuildS3LibraryPagingAndFileDetailCallbacks() {
        S3MediaLibraryFile file = new S3MediaLibraryFile("movie.mkv", "media-library/movie.mkv", 1024L, LocalDateTime.now());
        S3MediaLibraryService s3MediaLibraryService = mock(S3MediaLibraryService.class);
        when(s3MediaLibraryService.fileKey(file)).thenReturn("file-key");

        String keyboard = factory.s3MediaLibraryKeyboard(List.of(file), s3MediaLibraryService, 0, 10);

        assertThat(keyboard).contains("s3:file:file-key", "menu:library:s3:page:0", "menu:libraries");
    }

    private Set<String> callbacks(String keyboard) throws Exception {
        Set<String> callbacks = new LinkedHashSet<>();
        for (JsonNode row : objectMapper.readTree(keyboard).path("inline_keyboard")) {
            for (JsonNode button : row) {
                if (button.hasNonNull("callback_data")) {
                    callbacks.add(button.path("callback_data").asText());
                }
            }
        }
        return callbacks;
    }
}
