package ru.xataaa.torrentbot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ru.xataaa.torrentbot.common.*;
import ru.xataaa.torrentbot.config.*;
import ru.xataaa.torrentbot.downloadlink.HomeDownloadLinkService;
import ru.xataaa.torrentbot.file.DownloadFileRepository;
import ru.xataaa.torrentbot.job.*;
import ru.xataaa.torrentbot.llm.*;
import ru.xataaa.torrentbot.media.*;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;
import ru.xataaa.torrentbot.regression.UiRegression;
import ru.xataaa.torrentbot.telegram.handler.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@UiRegression
class UxFlowsTest {
    final TelegramMessageService messages = mock(TelegramMessageService.class);
    final TelegramKeyboardFactory keyboards = new TelegramKeyboardFactory();
    final AppProperties app = mock(AppProperties.class);
    final MediaLibraryService local = mock(MediaLibraryService.class);
    final HomeWebdavMediaLibraryService home = mock(HomeWebdavMediaLibraryService.class);
    final HomeWebdavCleanupService homeCleanup = mock(HomeWebdavCleanupService.class);
    final S3MediaLibraryService s3 = mock(S3MediaLibraryService.class);
    final MovieDownloadConfirmationService preferences = mock(MovieDownloadConfirmationService.class);

    MenuCallbackHandler menu() {
        var menu = new MenuCallbackHandler(messages, keyboards, mock(DiskSpaceService.class),
                mock(FileSizeFormatter.class), local, home, s3, mock(HomeDownloadLinkService.class),
                mock(TimeProvider.class), mock(TaskOverviewService.class));
        ReflectionTestUtils.setField(menu, "confirmations", preferences);
        return menu;
    }

    MediaCleanupCallbackHandler cleanup() {
        when(app.isChatAllowed(42L)).thenReturn(true);
        return new MediaCleanupCallbackHandler(app, local, homeCleanup, s3, mock(FileSizeFormatter.class), messages, keyboards);
    }

    String latestCallback(String prefix) throws Exception {
        var keyboard = ArgumentCaptor.forClass(String.class);
        verify(messages, atLeastOnce()).editText(eq(42L), eq(100L), anyString(), keyboard.capture());
        for (var row : new ObjectMapper().readTree(keyboard.getValue()).path("inline_keyboard"))
            for (var button : row) {
                String data = button.path("callback_data").asText();
                if (data.startsWith(prefix)) return data;
            }
        throw new AssertionError("Missing action " + prefix);
    }

    @Test void startInstallsFourPersistentButtonsWithDirectRoutes() throws Exception {
        new StartCommandHandler(messages, keyboards, menu()).handle(42L, "/start");
        var markup = ArgumentCaptor.forClass(String.class);
        verify(messages).sendTextWithInlineKeyboard(eq(42L), argThat(t -> t.length() < 300), markup.capture());
        var json = new ObjectMapper().readTree(markup.getValue());
        assertThat(json.path("is_persistent").asBoolean()).isTrue();
        assertThat(json.path("keyboard").size()).isEqualTo(2);
        for (var row : json.path("keyboard")) {
            assertThat(row.size()).isEqualTo(2);
            for (var button : row) assertThat(TelegramLlmCommandRouter.navigation(button.path("text").asText())).isNotNull();
        }
    }

    @Test void navigationWinsOverPendingNumberAndDisabledLlm() {
        var llm = mock(LlmRouter.class);
        var states = mock(UserDialogStateRepository.class);
        var destination = menu();
        when(app.isChatAllowed(42L)).thenReturn(true);
        var router = new TelegramLlmCommandRouter(mock(LlmProperties.class), app, llm, states,
                mock(TelegramCommandRouter.class), mock(TorrentSearchMessageHandler.class), mock(TasksCommandHandler.class),
                mock(LibraryCommandHandler.class), mock(HelpCommandHandler.class), mock(SettingsCommandHandler.class),
                mock(UnknownMessageHandler.class), destination, cleanup(), keyboards, messages);
        ReflectionTestUtils.setField(router, "confirmations", preferences);
        router.route(42L, "🎬 Медиатека");
        verify(preferences, atLeastOnce()).leaveInput(42L);
        verify(preferences, never()).consumeInput(anyLong(), anyString());
        verifyNoInteractions(llm);
        verify(messages).sendTextWithInlineKeyboard(eq(42L), contains("Где находятся"), contains("menu:library:s3"));
    }

    @Test void restartAndCancelAreRecognizedWithBotMention() {
        assertThat(TelegramLlmCommandRouter.navigation("/start@magnet_bott payload")).isEqualTo("start");
        assertThat(TelegramLlmCommandRouter.navigation("/cancel@magnet_bott")).isEqualTo("menu:home");
        assertThat(TelegramLlmCommandRouter.navigation("/startling")).isNull();
        assertThat(TelegramLlmCommandRouter.navigation("Матрица")).isNull();
    }

    @Test void libraryCommandAndButtonOpenSameHubWithoutFetchingStorage() {
        new LibraryCommandHandler(messages, menu()).handle(42L, "/library");
        verify(messages).sendTextWithInlineKeyboard(eq(42L), contains("Медиатека"), eq(keyboards.libraryMenuKeyboard()));
        verifyNoInteractions(local, home, s3);
    }

    @Test void settingsCallbackEditsOriginalScreen() {
        menu().handle("q", 42L, 100L, "menu:settings");
        verify(preferences).settings(42L, 100L);
        verify(preferences, never()).settings(42L);
    }

    @Test void unavailableHomeIsNotReportedAsEmpty() {
        when(home.isEnabled()).thenReturn(true);
        when(home.listItems()).thenThrow(new IllegalStateException("offline"));
        menu().handle("q", 42L, 100L, "menu:library:home");
        verify(messages).editText(eq(42L), eq(100L), contains("ПК недоступен"), contains("Обновить"));
    }

    @Test void emptyHomeHasSearchAndBack() {
        when(home.isEnabled()).thenReturn(true);
        when(home.listItems()).thenReturn(List.of());
        menu().handle("q", 42L, 100L, "menu:library:home");
        verify(messages).editText(eq(42L), eq(100L), contains("Фильмов пока нет"), argThat(k -> k.contains("menu:search") && k.contains("menu:libraries")));
    }

    @Test void unavailableS3HasRecovery() {
        when(s3.isEnabled()).thenReturn(true);
        when(s3.listFiles()).thenThrow(new IllegalStateException("offline"));
        menu().handle("q", 42L, 100L, "menu:library:s3");
        verify(messages).editText(eq(42L), eq(100L), contains("временно недоступно"), contains("menu:library:s3"));
    }

    @Test void cleanupTargetDoesNotDeleteAndTokenCanBeUsedOnlyOnce() throws Exception {
        var handler = cleanup();
        handler.handle("q", 42L, 100L, "media:cleanup:confirm:local");
        verifyNoInteractions(local, homeCleanup, s3);
        String execute = latestCallback("media:cleanup:execute:");
        when(local.cleanupAllFiles()).thenReturn(new MediaLibraryCleanupResult(2, 100));
        handler.handle("q2", 42L, 100L, execute);
        handler.handle("q3", 42L, 100L, execute);
        verify(local, times(1)).cleanupAllFiles();
    }

    @Test void cancelInvalidatesDestructiveButton() throws Exception {
        var handler = cleanup();
        handler.handle("q", 42L, 100L, "media:cleanup:confirm:home");
        String execute = latestCallback("media:cleanup:execute:");
        String cancel = latestCallback("media:cleanup:cancel:");
        handler.handle("q2", 42L, 100L, cancel);
        handler.handle("q3", 42L, 100L, execute);
        verifyNoInteractions(local, homeCleanup, s3);
    }

    @Test void cleanupCannotBeConfirmedInAnotherChatOrMessage() throws Exception {
        var handler = cleanup();
        when(app.isChatAllowed(43L)).thenReturn(true);
        handler.handle("q", 42L, 100L, "media:cleanup:confirm:s3");
        String execute = latestCallback("media:cleanup:execute:");
        handler.handle("q2", 43L, 100L, execute);
        handler.handle("q3", 42L, 101L, execute);
        verifyNoInteractions(local, homeCleanup, s3);
    }

    @Test void missingConfirmationAfterRestartCannotDelete() {
        cleanup().handle("q", 42L, 100L, "media:cleanup:execute:missing");
        verifyNoInteractions(local, homeCleanup, s3);
        verify(messages).answerCallbackQuery(eq("q"), contains("устарело"));
    }

    @Test void targetDoubleClickStartsOnlyOneJobAndForeignLookupDoesNotConsume() {
        var cache = new DownloadTargetSelectionCache();
        var jobs = mock(DownloadJobService.class);
        String id = cache.put(42L, "magnet:test", 10, "Movie");
        assertThat(cache.find(id, 43L)).isEmpty();
        var handler = new DownloadTargetSelectionCallbackHandler(cache, jobs, messages, s3);
        handler.handle("q", 42L, 100L, "target:select:" + id + ":HOME_PC");
        handler.handle("q2", 42L, 100L, "target:select:" + id + ":HOME_PC");
        verify(jobs, times(1)).startDownload(42L, "magnet:test", 10, DownloadTarget.HOME_PC, "Movie");
    }

    @Test void targetCancelPreventsOldDownloadButton() {
        var cache = new DownloadTargetSelectionCache();
        var jobs = mock(DownloadJobService.class);
        String id = cache.put(42L, "magnet:test", 10, "Movie");
        var handler = new DownloadTargetSelectionCallbackHandler(cache, jobs, messages, s3);
        handler.handle("q", 42L, 100L, "target:select:" + id + ":CANCEL");
        handler.handle("q2", 42L, 100L, "target:select:" + id + ":HOME_PC");
        verifyNoInteractions(jobs);
    }

    @Test void unknownCallbackOffersFreshNavigation() {
        new TelegramCallbackRouter(List.of(), messages).route("q", 42L, 100L, "obsolete:action");
        verify(messages).sendTextWithInlineKeyboard(eq(42L), contains("больше не поддерживается"), contains("menu:search"));
    }

    @Test void externalFailureOffersRecoveryWithoutRetryingAction() {
        var handler = mock(TelegramCallbackHandler.class);
        when(handler.supports("test")).thenReturn(true);
        doThrow(new IllegalStateException("offline")).when(handler).handle("q", 42L, 100L, "test");
        new TelegramCallbackRouter(List.of(handler), messages).route("q", 42L, 100L, "test");
        verify(handler, times(1)).handle("q", 42L, 100L, "test");
        verify(messages).sendTextWithInlineKeyboard(eq(42L), contains("проверь"), contains("menu:tasks"));
    }

    @Test void staleAndForeignFileSelectionsCannotChangeFiles() {
        var files = mock(DownloadFileRepository.class);
        var jobs = mock(DownloadJobRepository.class);
        var torrents = mock(QbittorrentTorrentService.class);
        UUID id = UUID.randomUUID();
        when(jobs.findById(id)).thenReturn(java.util.Optional.of(DownloadJob.builder().id(id).chatId(43L)
                .status(DownloadJobStatus.WAITING_FILE_SELECTION).build()));
        var handler = new FileSelectionCallbackHandler(files, jobs, mock(DownloadOrchestrator.class), messages,
                torrents, mock(DiskSpaceService.class), mock(FileSizeFormatter.class), mock(FileSelectionViewFactory.class));
        handler.handle("q", 42L, 100L, "file:select:all:" + id);
        when(jobs.findById(id)).thenReturn(java.util.Optional.of(DownloadJob.builder().id(id).chatId(42L)
                .status(DownloadJobStatus.DOWNLOADING).build()));
        handler.handle("q2", 42L, 100L, "file:select:all:" + id);
        handler.handle("q3", 42L, 100L, "file:select:all:bad-id");
        verifyNoInteractions(files, torrents);
    }

    @Test void singleFileDeleteNeedsFreshConfirmationAndRejectsDuplicate() {
        var file = new S3MediaLibraryFile("movie.mkv", "media/movie.mkv", 100, java.time.LocalDateTime.of(2026, 9, 6, 12, 0));
        when(s3.findFileByKey("key")).thenReturn(file);
        when(s3.listFiles()).thenReturn(List.of());
        var handler = new S3MediaCallbackHandler(s3, messages, keyboards, mock(FileSizeFormatter.class), mock(TimeProvider.class));
        handler.handle("old", 42L, 100L, "s3:delete:confirm:key");
        verify(s3, never()).deleteFile(anyString());
        handler.handle("ask", 42L, 100L, "s3:delete:ask:key");
        handler.handle("yes", 42L, 100L, "s3:delete:confirm:key");
        handler.handle("again", 42L, 100L, "s3:delete:confirm:key");
        verify(s3, times(1)).deleteFile("media/movie.mkv");
    }

    @Test void changedFileAndCancelledConfirmationAreNotDeleted() {
        var first = new S3MediaLibraryFile("movie.mkv", "media/movie.mkv", 100, null);
        var changed = new S3MediaLibraryFile("movie.mkv", "media/movie.mkv", 200, null);
        when(s3.findFileByKey("key")).thenReturn(first);
        var handler = new S3MediaCallbackHandler(s3, messages, keyboards, mock(FileSizeFormatter.class), mock(TimeProvider.class));
        handler.handle("ask", 42L, 100L, "s3:delete:ask:key");
        when(s3.findFileByKey("key")).thenReturn(changed);
        handler.handle("yes", 42L, 100L, "s3:delete:confirm:key");
        handler.handle("ask2", 42L, 100L, "s3:delete:ask:key");
        handler.handle("cancel", 42L, 100L, "s3:delete:cancel:key");
        handler.handle("old", 42L, 100L, "s3:delete:confirm:key");
        verify(s3, never()).deleteFile(anyString());
    }

    @Test void fileSelectionDoubleClickKeepsDesiredStateAndOffersLater() throws Exception {
        var files = mock(DownloadFileRepository.class);
        var jobs = mock(DownloadJobRepository.class);
        UUID jobId = UUID.randomUUID(), fileId = UUID.randomUUID();
        var file = ru.xataaa.torrentbot.file.DownloadFile.builder().id(fileId).jobId(jobId).fileName("episode.mkv")
                .status(ru.xataaa.torrentbot.file.DownloadFileStatus.SKIPPED_BY_USER).sizeBytes(100).build();
        when(files.findById(fileId)).thenReturn(java.util.Optional.of(file));
        when(files.findByJobIdAndStatuses(eq(jobId), anyList())).thenReturn(List.of(file));
        when(jobs.findById(jobId)).thenReturn(java.util.Optional.of(DownloadJob.builder().id(jobId).chatId(42L)
                .status(DownloadJobStatus.WAITING_FILE_SELECTION).build()));
        doAnswer(call -> { file.setStatus(call.getArgument(1)); return null; }).when(files).updateStatus(eq(fileId), any());
        var view = new FileSelectionViewFactory(mock(FileSizeFormatter.class));
        var handler = new FileSelectionCallbackHandler(files, jobs, mock(DownloadOrchestrator.class), messages,
                mock(QbittorrentTorrentService.class), mock(DiskSpaceService.class), mock(FileSizeFormatter.class), view);
        handler.handle("q", 42L, 100L, "file:set:" + fileId + ":0:1");
        handler.handle("q2", 42L, 100L, "file:set:" + fileId + ":0:1");
        assertThat(file.getStatus()).isEqualTo(ru.xataaa.torrentbot.file.DownloadFileStatus.READY_TO_UPLOAD);
        assertThat(view.keyboard(List.of(file), jobId, 0)).contains("menu:tasks", "file:set:" + fileId + ":0:0");
    }
}
