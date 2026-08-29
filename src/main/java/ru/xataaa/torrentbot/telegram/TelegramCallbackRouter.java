package ru.xataaa.torrentbot.telegram;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramCallbackRouter {

    private final List<TelegramCallbackHandler> callbackHandlers;
    private final TelegramMessageService telegramMessageService;

    public void route(String callbackQueryId, Long chatId, Long messageId, String data) {
        for (TelegramCallbackHandler callbackHandler : callbackHandlers) {
            if (callbackHandler.supports(data)) {
                callbackHandler.handle(callbackQueryId, chatId, messageId, data);
                return;
            }
        }
        log.warn("Unknown callback received: chatId={}, data={}", chatId, safeData(data));
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Неизвестное действие");
    }

    public void routeInline(String callbackQueryId, String inlineMessageId, Long userId, String data) {
        for (TelegramCallbackHandler callbackHandler : callbackHandlers) {
            if (callbackHandler.supports(data)) {
                callbackHandler.handleInline(callbackQueryId, inlineMessageId, userId, data);
                return;
            }
        }
        log.warn("Unknown inline callback received: userId={}, data={}", userId, safeData(data));
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Неизвестное действие");
    }

    private String safeData(String data) {
        if (data == null) {
            return "";
        }
        return data.length() <= 32 ? data : data.substring(0, 32);
    }
}
