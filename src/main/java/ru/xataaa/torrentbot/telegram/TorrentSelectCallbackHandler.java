package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.torrentsearch.ReleaseType;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchCache;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchResult;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchService;
import ru.xataaa.torrentbot.torrentsearch.TorrentTitleParser;

@Slf4j
@Component
@RequiredArgsConstructor
public class TorrentSelectCallbackHandler implements TelegramCallbackHandler {

    private static final String PREFIX = "torrent:select:";
    private static final String CONFIRM_PREFIX = "torrent:confirm:";
    private static final String PAGE_PREFIX = "torrent:page:";

    private final TorrentSearchCache torrentSearchCache;
    private final TorrentSearchService torrentSearchService;
    private final DownloadTargetSelectionService downloadTargetSelectionService;
    private final TelegramMessageService telegramMessageService;
    private final FileSizeFormatter fileSizeFormatter;
    private final TorrentTitleParser torrentTitleParser;

    @Override
    public boolean supports(String data) {
        return data != null && (data.startsWith(PREFIX) || data.startsWith(CONFIRM_PREFIX) || data.startsWith(PAGE_PREFIX));
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        if (data.startsWith(PAGE_PREFIX)) {
            handlePage(callbackQueryId, chatId, messageId, data);
            return;
        }
        if (data.startsWith(CONFIRM_PREFIX)) {
            handleConfirmedSelection(callbackQueryId, chatId, data.substring(CONFIRM_PREFIX.length()));
            return;
        }
        String selectionId = data.substring(PREFIX.length());
        TorrentSearchResult torrentSearchResult = torrentSearchCache.find(selectionId).orElse(null);
        if (!validateResult(callbackQueryId, chatId, torrentSearchResult)) {
            return;
        }
        if (requiresSeasonPackConfirmation(torrentSearchResult)) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Нужно подтверждение");
            telegramMessageService.sendTextWithInlineKeyboard(chatId, seasonPackConfirmationText(torrentSearchResult), seasonPackConfirmationKeyboard(selectionId));
            return;
        }
        startTargetSelection(callbackQueryId, chatId, torrentSearchResult);
    }

    private void handleConfirmedSelection(String callbackQueryId, Long chatId, String selectionId) {
        TorrentSearchResult torrentSearchResult = torrentSearchCache.find(selectionId).orElse(null);
        if (!validateResult(callbackQueryId, chatId, torrentSearchResult)) {
            return;
        }
        startTargetSelection(callbackQueryId, chatId, torrentSearchResult);
    }

    private boolean validateResult(String callbackQueryId, Long chatId, TorrentSearchResult torrentSearchResult) {
        if (torrentSearchResult == null) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Результат устарел");
            telegramMessageService.sendText(chatId, "Этот результат поиска устарел. Повтори поиск ещё раз.");
            return false;
        }
        if (!torrentSearchResult.hasMagnet()) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Нет magnet-ссылки");
            telegramMessageService.sendText(chatId, "У этой раздачи нет magnet-ссылки. Выбери другой результат.");
            return false;
        }
        return true;
    }

    private void startTargetSelection(String callbackQueryId, Long chatId, TorrentSearchResult torrentSearchResult) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Запускаю загрузку");
        log.info("Torrent selected from search: chatId={}, title={}, seeders={}",
                chatId, torrentSearchResult.title(), torrentSearchResult.seeders());
        downloadTargetSelectionService.askTarget(chatId, torrentSearchResult.magnetUri(), torrentSearchResult.sizeBytes(), torrentSearchResult.title());
    }

    private boolean requiresSeasonPackConfirmation(TorrentSearchResult torrentSearchResult) {
        TorrentTitleParser.ParsedTorrentTitle parsed = torrentTitleParser.parse(torrentSearchResult);
        return parsed.releaseType() == ReleaseType.SEASON_PACK || parsed.releaseType() == ReleaseType.MULTI_SEASON_PACK;
    }

    private String seasonPackConfirmationText(TorrentSearchResult torrentSearchResult) {
        TorrentTitleParser.ParsedTorrentTitle parsed = torrentTitleParser.parse(torrentSearchResult);
        StringBuilder text = new StringBuilder();
        text.append("Проверь перед скачиванием\n\n");
        text.append("Эта раздача похожа на ");
        if (parsed.releaseType() == ReleaseType.MULTI_SEASON_PACK) {
            text.append("пак нескольких сезонов");
        } else {
            text.append("сезон целиком");
        }
        text.append(".\n");
        if (parsed.seasonNumber() != null) {
            text.append("Сезон: ").append(parsed.seasonNumber()).append("\n");
        }
        if (!parsed.episodeNumbers().isEmpty()) {
            text.append("Серии внутри названия: ").append(parsed.episodeNumbers()).append("\n");
        }
        if (torrentSearchResult.selectedSeasonNumber() != null || !torrentSearchResult.selectedEpisodeNumbers().isEmpty()) {
            text.append("Вы выбрали: ");
            if (torrentSearchResult.selectedSeasonNumber() != null) {
                text.append("сезон ").append(torrentSearchResult.selectedSeasonNumber());
            }
            if (!torrentSearchResult.selectedEpisodeNumbers().isEmpty()) {
                if (torrentSearchResult.selectedSeasonNumber() != null) {
                    text.append(", ");
                }
                text.append("серии ").append(torrentSearchResult.selectedEpisodeNumbers());
            }
            text.append("\n");
        }
        if (torrentSearchResult.sizeBytes() > 0) {
            text.append("Размер всей раздачи: ").append(fileSizeFormatter.format(torrentSearchResult.sizeBytes())).append("\n");
        }
        text.append("\nПосле выбора места я загружу metadata, остановлю torrent и покажу файлы/серии для выбора. ");
        text.append("Скачивание продолжится только после кнопки «Скачать выбранные» или явного выбора «Скачать всё».");
        return text.toString();
    }

    private String seasonPackConfirmationKeyboard(String selectionId) {
        return """
                {"inline_keyboard":[
                  [{"text":"Выбрать место и затем файлы","callback_data":"torrent:confirm:%s"}],
                  [{"text":"Новый поиск","callback_data":"menu:search"}]
                ]}
                """.formatted(selectionId);
    }

    private void handlePage(String callbackQueryId, Long chatId, Long messageId, String data) {
        String[] parts = data.substring(PAGE_PREFIX.length()).split(":");
        if (parts.length != 2) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Не понял страницу");
            return;
        }
        try {
            TorrentSearchService.SearchPage searchPage = torrentSearchService.findPage(parts[0], Integer.parseInt(parts[1]));
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Страница " + (searchPage.pageNumber() + 1));
            telegramMessageService.editText(
                    chatId,
                    messageId,
                    torrentSearchService.formatPageMessage(searchPage),
                    torrentSearchService.resultsKeyboard(searchPage)
            );
        } catch (RuntimeException runtimeException) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Поиск устарел");
            telegramMessageService.sendText(chatId, "Этот поиск устарел. Напиши название фильма ещё раз.");
        }
    }
}
