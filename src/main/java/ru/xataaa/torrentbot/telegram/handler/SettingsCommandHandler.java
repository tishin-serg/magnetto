package ru.xataaa.torrentbot.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.telegram.MovieDownloadConfirmationService;

@Component
@RequiredArgsConstructor
public class SettingsCommandHandler implements TelegramMessageHandler {
    private final MovieDownloadConfirmationService confirmations;
    @Override public boolean supports(String text) {
        return text != null && text.trim().matches("(?s)^/settings(?:@\\w+)?(?:\\s.*)?$");
    }
    @Override public void handle(Long chatId, String text) { confirmations.settings(chatId); }
}