package ru.xataaa.torrentbot.telegram.handler;

import org.springframework.stereotype.Component;

@Component
public class InlineSelectionMessageHandler implements TelegramMessageHandler {

    @Override
    public boolean supports(String text) {
        if (text == null) {
            return false;
        }
        String trimmedText = text.trim();
        return trimmedText.startsWith("Выбран фильм:")
                || trimmedText.startsWith("Выбран сериал:")
                || trimmedText.startsWith("Р’С‹Р±СЂР°РЅ ");
    }

    @Override
    public void handle(Long chatId, String text) {
        // The user selected an inline movie card. The inline keyboard handles the next action.
    }
}
