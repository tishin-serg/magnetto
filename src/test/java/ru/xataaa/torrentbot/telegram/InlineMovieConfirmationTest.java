package ru.xataaa.torrentbot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.regression.UiRegression;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import ru.xataaa.torrentbot.telegram.dto.TelegramUpdate;
import ru.xataaa.torrentbot.movie.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ru.xataaa.torrentbot.torrentsearch.*;

@UiRegression
class InlineMovieConfirmationTest {
    @Test void inlineMessageOpensMovieWithoutExtraButton() throws Exception {
        var api = mock(TelegramBotApiClient.class);
        var offset = mock(TelegramPollingStateRepository.class);
        var router = mock(TelegramLlmCommandRouter.class);
        var movies = mock(MovieSelectionCallbackHandler.class);
        var polling = new TelegramPollingService(api, offset, router, mock(TelegramCallbackRouter.class),
                mock(TelegramInlineQueryRouter.class), Runnable::run);
        ReflectionTestUtils.setField(polling, "movieSelections", movies);
        var update = new ObjectMapper().readValue("""
                {"update_id":1,"message":{"message_id":7,"chat":{"id":42},"via_bot":{"id":99},
                "text":"Выбран фильм: Матрица",
                "reply_markup":{"inline_keyboard":[[{"text":"Подобрать","callback_data":"movie:open:abc"}]]}}}
                """, TelegramUpdate.class);
        when(api.getUpdates(null)).thenReturn(List.of(update));
        when(offset.getOffset()).thenReturn(Optional.empty());
        polling.pollUpdates();
        verify(movies).openFromInline(42L, "abc");
        verifyNoInteractions(router);
    }

    @Test void inlineFeedbackAndMessageDoNotProduceTwoConfirmations() {
        var metadata = mock(MovieMetadataService.class);
        var confirmations = mock(MovieDownloadConfirmationService.class);
        var handler = new MovieSelectionCallbackHandler(metadata, mock(MovieSearchSessionService.class),
                mock(MovieSearchViewFactory.class), mock(TorrentSearchService.class),
                mock(TorrentAvailabilityService.class), mock(TelegramMessageService.class), new SimpleMeterRegistry());
        ReflectionTestUtils.setField(handler, "confirmations", confirmations);
        var movie = new MovieMetadata("abc", "603", MovieMediaType.MOVIE, "Матрица", "", 1999, null, "", "");
        when(metadata.findBySelectionId("abc")).thenReturn(Optional.of(movie));
        handler.openFromInline(42L, "abc");
        handler.openFromInline(42L, "abc");
        verify(confirmations, times(1)).open(42L, null, movie);
    }
}
