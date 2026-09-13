package ru.xataaa.torrentbot.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

@Component
@RequiredArgsConstructor
public class TelegramDownloadNotifier implements DownloadNotifier {
    private final TelegramMessageService telegram;
    @Override public void accepted(Long chatId, java.util.UUID jobId) {
        if (chatId != null && chatId != 0) telegram.sendText(chatId, "Задача принята: " + jobId);
    }
}
