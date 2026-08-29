package ru.xataaa.torrentbot.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.MagnetValidator;
import ru.xataaa.torrentbot.telegram.DownloadTargetSelectionService;

@Component
@RequiredArgsConstructor
public class MagnetMessageHandler implements TelegramMessageHandler {

    private final MagnetValidator magnetValidator;
    private final DownloadTargetSelectionService downloadTargetSelectionService;

    @Override
    public boolean supports(String text) {
        return magnetValidator.isValid(text);
    }

    @Override
    public void handle(Long chatId, String text) {
        downloadTargetSelectionService.askTarget(chatId, text.trim(), 0L, null);
    }
}
