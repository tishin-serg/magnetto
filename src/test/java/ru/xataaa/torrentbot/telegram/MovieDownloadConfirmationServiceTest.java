package ru.xataaa.torrentbot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.job.*;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;
import ru.xataaa.torrentbot.movie.*;
import ru.xataaa.torrentbot.preferences.*;
import ru.xataaa.torrentbot.torrentsearch.*;

class MovieDownloadConfirmationServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final AppProperties app = mock(AppProperties.class);
    final DownloadPreferencesRepository preferences = mock(DownloadPreferencesRepository.class);
    final TorrentAvailabilityService availability = mock(TorrentAvailabilityService.class);
    final TelegramMessageService messages = mock(TelegramMessageService.class);
    final DownloadJobService jobs = mock(DownloadJobService.class);
    final S3MediaLibraryService s3 = mock(S3MediaLibraryService.class);
    final MovieMetadata movie = new MovieMetadata("m", "603", MovieMediaType.MOVIE, "Матрица", "", 1999, null, "", "");
    final MovieDownloadConfirmationService service = new MovieDownloadConfirmationService(app, preferences, availability,
            mock(TorrentSearchService.class), messages, jobs, s3, mapper);

    @BeforeEach void setup() {
        when(app.isChatAllowed(42L)).thenReturn(true);
        when(preferences.find(42L)).thenReturn(DownloadPreferences.defaults());
        when(availability.catalog(movie)).thenReturn(new TorrentAvailabilityCatalog(movie,
                List.of(item("Large", 16, 100), item("Low seeds", 8, 9), item("First", 8, 50),
                        item("Second", 7, 50), item("Too small", 3, 999), item("CAMRip", 6, 500), item("Матрица Трилогия 1999-2003", 12, 999))));
    }

    TorrentAvailabilityItem item(String title, int gb, int seeds) {
        var result = new TorrentSearchResult(title, title, "", "magnet:?xt=urn:btih:" + title,
                "", "", gb * DownloadPreferences.GB, seeds, 0, "");
        return new TorrentAvailabilityItem(result, null, null, ReleaseType.MOVIE, "1080p", "Дубляж");
    }

    String callback(String label) throws Exception {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(messages, atLeastOnce()).sendTextWithInlineKeyboard(eq(42L), anyString(), captor.capture());
        return find(captor.getValue(), label);
    }

    String editedCallback(String label) throws Exception {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(messages, atLeastOnce()).editText(eq(42L), eq(100L), anyString(), captor.capture());
        return find(captor.getValue(), label);
    }

    String find(String keyboard, String label) throws Exception {
        for (var row : mapper.readTree(keyboard).path("inline_keyboard"))
            for (var button : row)
                if (button.path("text").asText().equals(label)) return button.path("callback_data").asText();
        throw new AssertionError("Missing button: " + label + " in " + keyboard);
    }

    @Test void filtersAndRequiresConfirmationAndRejectsDuplicate() throws Exception {
        service.open(42L, null, movie);
        verifyNoInteractions(jobs);
        verify(messages).sendTextWithInlineKeyboard(eq(42L), contains("Раздача: Second"), anyString());
        String confirm = callback("✅ Скачать");
        service.handle("q", 42L, 100L, confirm);
        service.handle("q2", 42L, 100L, confirm);
        verify(jobs, times(1)).startDownload(eq(42L), contains("Second"), eq(7 * DownloadPreferences.GB),
                eq(DownloadTarget.HOME_PC), eq("Second"), eq(DownloadPreferences.defaults()));
    }

    @Test void changingVariantInvalidatesOldConfirmation() throws Exception {
        service.open(42L, null, movie);
        String old = callback("✅ Скачать");
        service.handle("q", 42L, 100L, callback("Другой вариант"));
        service.handle("q2", 42L, 100L, old);
        verifyNoInteractions(jobs);
        service.handle("q3", 42L, 100L, editedCallback("✅ Скачать"));
        verify(jobs).startDownload(eq(42L), contains("First"), anyLong(), any(), eq("First"), any());
    }

    @Test void anotherUserCannotConfirmAndCancelInvalidates() throws Exception {
        service.open(42L, null, movie);
        String confirm = callback("✅ Скачать");
        when(app.isChatAllowed(43L)).thenReturn(true);
        service.handle("q", 43L, 100L, confirm);
        service.handle("q2", 42L, 100L, callback("Отмена"));
        service.handle("q3", 42L, 100L, confirm);
        verifyNoInteractions(jobs);
    }

    @Test void noMatchesHasNoDownloadButton() throws Exception {
        when(availability.catalog(movie)).thenReturn(new TorrentAvailabilityCatalog(movie, List.of(item("Tiny", 1, 40))));
        service.open(42L, null, movie);
        verify(messages).sendTextWithInlineKeyboard(eq(42L), contains("Подходящих раздач нет"), argThat(value -> !value.contains("pref:confirm:")));
        verifyNoInteractions(jobs);
    }

    @Test void persistentSettingsValidateAndSaveDecimalInput() throws Exception {
        service.settings(42L);
        service.handle("q", 42L, 100L, callback("Размер от"));
        assertThat(service.consumeInput(42L, "16")).isTrue();
        verify(preferences, never()).save(anyLong(), any());
        assertThat(service.consumeInput(42L, "4,5")).isTrue();
        verify(preferences).save(42L, new DownloadPreferences(4_500_000_000L, 15 * DownloadPreferences.GB, 10, DownloadTarget.HOME_PC));
        verifyNoInteractions(jobs);
    }

    @Test void requestEditsDoNotChangeDefaultsOrPermitOldConfirmation() throws Exception {
        service.open(42L, null, movie);
        String old = callback("✅ Скачать");
        service.handle("q", 42L, 100L, callback("Изменить условия"));
        service.handle("q2", 42L, 100L, editedCallback("Размер до"));
        assertThat(service.consumeInput(42L, "6")).isTrue();
        service.handle("q3", 42L, 100L, old);
        verifyNoInteractions(jobs);
        verify(preferences, never()).save(anyLong(), any());
        service.handle("q4", 42L, 100L, callback("Подобрать раздачу"));
        verify(messages).editText(eq(42L), eq(100L), contains("Подходящих раздач нет"), anyString());
    }

    @Test void unavailableS3DoesNotCreateJob() throws Exception {
        when(preferences.find(42L)).thenReturn(new DownloadPreferences(0, 15 * DownloadPreferences.GB, 10, DownloadTarget.S3));
        service.open(42L, null, movie);
        service.handle("q", 42L, 100L, callback("✅ Скачать"));
        verifyNoInteractions(jobs);
        verify(messages).sendText(eq(42L), contains("S3 сейчас недоступен"));
    }

    @Test void boundariesUnknownSizeAndSeeders() {
        var p = DownloadPreferences.defaults();
        assertThat(p.accepts(item("min", 4, 10).result())).isTrue();
        assertThat(p.accepts(item("max", 15, 10).result())).isTrue();
        assertThat(p.accepts(item("unknown", 0, 10).result())).isFalse();
        assertThat(p.accepts(item("unknown seeds", 8, 0).result())).isFalse();
        assertThatThrownBy(() -> p.with("min", "-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.with("max", "NaN")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.with("seeds", "1.5")).isInstanceOf(IllegalArgumentException.class);
    }
}