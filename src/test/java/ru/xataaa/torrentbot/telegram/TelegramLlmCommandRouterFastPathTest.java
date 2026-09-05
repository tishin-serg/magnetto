package ru.xataaa.torrentbot.telegram;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.config.LlmProperties;
import ru.xataaa.torrentbot.llm.LlmRouter;
import ru.xataaa.torrentbot.llm.UserDialogStateRepository;
import ru.xataaa.torrentbot.telegram.handler.HelpCommandHandler;
import ru.xataaa.torrentbot.telegram.handler.LibraryCommandHandler;
import ru.xataaa.torrentbot.telegram.handler.SettingsCommandHandler;
import ru.xataaa.torrentbot.telegram.handler.TasksCommandHandler;
import ru.xataaa.torrentbot.telegram.handler.TorrentSearchMessageHandler;
import ru.xataaa.torrentbot.telegram.handler.UnknownMessageHandler;

class TelegramLlmCommandRouterFastPathTest {

    @Test
    void shouldRouteLikelyTitleToSearchWithoutLlm() {
        LlmRouter llmRouter = mock(LlmRouter.class);
        TorrentSearchMessageHandler torrentSearchMessageHandler = mock(TorrentSearchMessageHandler.class);
        TelegramLlmCommandRouter router = router(llmRouter, torrentSearchMessageHandler);

        router.route(42L, "Матрица 1999");

        verify(torrentSearchMessageHandler).handle(42L, "Матрица 1999");
        verify(llmRouter, never()).route(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRouteTasksKeywordWithoutLlm() {
        LlmRouter llmRouter = mock(LlmRouter.class);
        TasksCommandHandler tasksCommandHandler = mock(TasksCommandHandler.class);
        TelegramLlmCommandRouter router = new TelegramLlmCommandRouter(
                new LlmProperties(true, "http://ollama", "model", 30, 0.6),
                new AppProperties(100, 100, 100, "", true, 30, 24, 3, 30, 48),
                llmRouter,
                mock(UserDialogStateRepository.class),
                mock(TelegramCommandRouter.class),
                mock(TorrentSearchMessageHandler.class),
                tasksCommandHandler,
                mock(LibraryCommandHandler.class),
                mock(HelpCommandHandler.class),
                mock(SettingsCommandHandler.class),
                mock(UnknownMessageHandler.class),
                mock(MenuCallbackHandler.class),
                mock(MediaCleanupCallbackHandler.class),
                mock(TelegramKeyboardFactory.class),
                mock(TelegramMessageService.class)
        );

        router.route(42L, "задачи");

        verify(tasksCommandHandler).handle(42L, "/tasks");
        verify(llmRouter, never()).route(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldIgnoreInlineMovieSelectionWithoutLlmOrDuplicateSearch() {
        LlmRouter llmRouter = mock(LlmRouter.class);
        TelegramCommandRouter legacyRouter = mock(TelegramCommandRouter.class);
        TorrentSearchMessageHandler torrentSearchMessageHandler = mock(TorrentSearchMessageHandler.class);
        TelegramLlmCommandRouter router = router(llmRouter, legacyRouter, torrentSearchMessageHandler);
        String selection = "Выбран фильм:\n\nУльтиматум Борна / The Bourne Ultimatum (2007)";

        router.route(42L, selection);

        verify(legacyRouter).route(42L, selection);
        verify(torrentSearchMessageHandler, never()).handle(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(llmRouter, never()).route(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    private TelegramLlmCommandRouter router(LlmRouter llmRouter, TorrentSearchMessageHandler torrentSearchMessageHandler) {
        return router(llmRouter, mock(TelegramCommandRouter.class), torrentSearchMessageHandler);
    }

    private TelegramLlmCommandRouter router(
            LlmRouter llmRouter,
            TelegramCommandRouter legacyRouter,
            TorrentSearchMessageHandler torrentSearchMessageHandler
    ) {
        return new TelegramLlmCommandRouter(
                new LlmProperties(true, "http://ollama", "model", 30, 0.6),
                new AppProperties(100, 100, 100, "", true, 30, 24, 3, 30, 48),
                llmRouter,
                mock(UserDialogStateRepository.class),
                legacyRouter,
                torrentSearchMessageHandler,
                mock(TasksCommandHandler.class),
                mock(LibraryCommandHandler.class),
                mock(HelpCommandHandler.class),
                mock(SettingsCommandHandler.class),
                mock(UnknownMessageHandler.class),
                mock(MenuCallbackHandler.class),
                mock(MediaCleanupCallbackHandler.class),
                mock(TelegramKeyboardFactory.class),
                mock(TelegramMessageService.class)
        );
    }
}
