package ru.xataaa.torrentbot.telegram.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.config.TelegramProperties;
import ru.xataaa.torrentbot.movie.MovieMetadataService;
import ru.xataaa.torrentbot.telegram.TelegramInlineResultFactory;
import ru.xataaa.torrentbot.telegram.TelegramKeyboardFactory;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchService;

class TorrentSearchMessageHandlerTest {

    @Test
    void shouldRouteSearchCommandDirectlyToTorrentSearch() {
        TorrentSearchService torrentSearchService = mock(TorrentSearchService.class);
        MovieMetadataService movieMetadataService = mock(MovieMetadataService.class);
        TelegramMessageService telegramMessageService = mock(TelegramMessageService.class);
        TorrentSearchService.SearchPage emptyPage = new TorrentSearchService.SearchPage(
                "search-id",
                "zxqv-no-tmdb-match-884421",
                List.of(),
                0,
                1,
                0
        );
        when(torrentSearchService.searchFirstPage("zxqv-no-tmdb-match-884421")).thenReturn(emptyPage);
        when(torrentSearchService.formatPageMessage(emptyPage)).thenReturn("Ничего не нашёл");

        TorrentSearchMessageHandler handler = new TorrentSearchMessageHandler(
                torrentSearchService,
                movieMetadataService,
                mock(TelegramInlineResultFactory.class),
                mock(TelegramKeyboardFactory.class),
                telegramMessageService,
                new AppProperties(100, 100, 100, "", true, 30, 24, 3, 30, 48),
                new TelegramProperties("token", "magnettto_bot", "http://telegram", 1000, 1000, 1000,
                        new TelegramProperties.FileProperties(100, 24, true))
        );

        handler.handle(42L, "/search zxqv-no-tmdb-match-884421");

        verify(telegramMessageService).sendTyping(42L);
        verify(torrentSearchService).searchFirstPage("zxqv-no-tmdb-match-884421");
        verify(movieMetadataService, never()).search("zxqv-no-tmdb-match-884421");
        verify(telegramMessageService).sendText(42L, "Ничего не нашёл");
    }
}