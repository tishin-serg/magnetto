package ru.xataaa.torrentbot.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.FileSizeFormatter;

@Service
@RequiredArgsConstructor
public class DownloadTargetSelectionService {

    private final DownloadTargetSelectionCache downloadTargetSelectionCache;
    private final TelegramMessageService telegramMessageService;
    private final FileSizeFormatter fileSizeFormatter;

    public void askTarget(Long chatId, String magnetUrl, long expectedSizeBytes, String title) {
        String selectionId = downloadTargetSelectionCache.put(chatId, magnetUrl, expectedSizeBytes, title);
        StringBuilder text = new StringBuilder();
        text.append("Куда скачать torrent?\n\n");
        if (title != null && !title.isBlank()) {
            text.append("Раздача: ").append(title).append("\n");
        }
        if (expectedSizeBytes > 0) {
            text.append("Размер: ").append(fileSizeFormatter.format(expectedSizeBytes)).append("\n");
        }
        text.append("\nЕсли внутри torrent несколько видеофайлов, я сначала загружу metadata и покажу выбор файлов/серий. ");
        text.append("Скачивание продолжится только после подтверждения выбранных файлов.\n\n");
        text.append("VPS скачивает на сервер как раньше.\n");
        text.append("Домашний ПК скачивает в qBittorrent на твоём компьютере через Tailscale.");
        telegramMessageService.sendTextWithInlineKeyboard(chatId, text.toString(), keyboard(selectionId));
    }

    private String keyboard(String selectionId) {
        return """
                {"inline_keyboard":[
                  [{"text":"💾 Скачать на VPS","callback_data":"target:select:%s:VPS"}],
                  [{"text":"🏠 Скачать на домашний ПК","callback_data":"target:select:%s:HOME_PC"}]
                ]}
                """.formatted(selectionId, selectionId);
    }
}
