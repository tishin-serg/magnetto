package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import java.util.List;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchResult;

@Service
@RequiredArgsConstructor
public class DownloadTargetSelectionService {

    private final DownloadTargetSelectionCache downloadTargetSelectionCache;
    private final TelegramMessageService telegramMessageService;
    private final FileSizeFormatter fileSizeFormatter;

    public void askTarget(Long chatId, String magnetUrl, long expectedSizeBytes, String title) {
        askTarget(chatId, magnetUrl, expectedSizeBytes, title, List.of());
    }

    public void askTarget(Long chatId, String magnetUrl, long expectedSizeBytes, String title,
                          List<TorrentSearchResult> alternatives) {
        String selectionId = downloadTargetSelectionCache.put(chatId, magnetUrl, expectedSizeBytes, title, alternatives);
        StringBuilder text = new StringBuilder();
        text.append("Куда скачать?\n\n");
        if (title != null && !title.isBlank()) {
            text.append("Раздача: ").append(title).append("\n");
        }
        if (expectedSizeBytes > 0) {
            text.append("Размер: ").append(fileSizeFormatter.format(expectedSizeBytes)).append("\n");
        }
        text.append("\nВыбор места запускает загрузку. Для раздачи с несколькими видео сначала предложу выбрать файлы.\n\n");
        text.append("Домашний ПК должен быть включён. Для S3 сначала скачаю на сервер VPS, затем перенесу в облако.");
        telegramMessageService.sendTextWithInlineKeyboard(chatId, text.toString(), keyboard(selectionId));
    }

    static String keyboard(String selectionId) {
        return """
                {"inline_keyboard":[
                  [{"text":"💾 Скачать на VPS","callback_data":"target:select:%s:VPS"}],
                  [{"text":"🏠 Скачать на домашний ПК","callback_data":"target:select:%s:HOME_PC"}],
                  [{"text":"☁️ Скачать в S3","callback_data":"target:select:%s:S3"}],
                  [{"text":"❌ Отмена","callback_data":"target:select:%s:CANCEL"}]
                ]}
                """.formatted(selectionId, selectionId, selectionId, selectionId);
    }
}
