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

    @org.springframework.beans.factory.annotation.Autowired
    private ru.xataaa.torrentbot.config.AppProperties appProperties;
    @org.springframework.beans.factory.annotation.Autowired
    private MovieDownloadConfirmationService confirmations;

    public void route(String callbackQueryId, Long chatId, Long messageId, String data) {
        if (appProperties != null && !appProperties.isChatAllowed(chatId)) {
            telegramMessageService.answerCallbackQuery(callbackQueryId, "Доступ запрещён");
            return;
        }
        if (confirmations != null && (data == null || !data.startsWith("pref:"))) confirmations.leaveInput(chatId);
        for (TelegramCallbackHandler callbackHandler : callbackHandlers) {
            if (callbackHandler.supports(data)) {
                try {
                    callbackHandler.handle(callbackQueryId, chatId, messageId, data);
                } catch (RuntimeException exception) {
                    log.warn("Callback failed: chatId={}, handler={}", chatId, callbackHandler.getClass().getSimpleName());
                    telegramMessageService.answerCallbackQuery(callbackQueryId, "Не удалось завершить действие");
                    telegramMessageService.sendTextWithInlineKeyboard(chatId,
                            "Не удалось завершить действие.\n\nОткрой раздел заново и проверь его состояние. Если запускалась загрузка или удаление, проверь результат перед повтором.",
                            new TelegramKeyboardFactory().mainMenuKeyboard());
                }
                return;
            }
        }
        log.warn("Unknown callback received: chatId={}, data={}", chatId, safeData(data));
        telegramMessageService.answerCallbackQuery(callbackQueryId, "Кнопка устарела. Открой главное меню.");
        telegramMessageService.sendTextWithInlineKeyboard(chatId, "Этот экран больше не поддерживается. Выбери раздел:",
                new TelegramKeyboardFactory().mainMenuKeyboard());
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
