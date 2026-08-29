package ru.xataaa.torrentbot.telegram;

import java.io.File;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.retry.RetryExecutor;
import ru.xataaa.torrentbot.telegram.dto.TelegramMessageResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMessageService {

    private final TelegramBotApiClient telegramBotApiClient;
    private final RetryExecutor retryExecutor;
    private final MeterRegistry meterRegistry;

    public TelegramMessageResponse sendText(Long chatId, String text) {
        return retryExecutor.execute("telegram.sendMessage", () -> telegramBotApiClient.sendMessage(chatId, text));
    }

    public TelegramMessageResponse sendTextWithInlineKeyboard(Long chatId, String text, String replyMarkupJson) {
        return retryExecutor.execute("telegram.sendMessageWithReplyMarkup", () -> telegramBotApiClient.sendMessageWithReplyMarkup(chatId, text, replyMarkupJson));
    }

    public TelegramMessageResponse sendForceReply(Long chatId, String text, String inputPlaceholder) {
        String replyMarkupJson = "{\"force_reply\":true,\"selective\":true,\"input_field_placeholder\":\""
                + escapeJson(inputPlaceholder)
                + "\"}";
        return retryExecutor.execute("telegram.sendForceReply", () -> telegramBotApiClient.sendMessageWithReplyMarkup(chatId, text, replyMarkupJson));
    }

    public TelegramMessageResponse editText(Long chatId, Long messageId, String text, String replyMarkupJson) {
        return retryExecutor.execute("telegram.editMessageText", () -> telegramBotApiClient.editMessageText(chatId, messageId, text, replyMarkupJson));
    }

    public void answerCallbackQuery(String callbackQueryId, String text) {
        retryExecutor.executeVoid("telegram.answerCallbackQuery", () -> telegramBotApiClient.answerCallbackQuery(callbackQueryId, text));
    }

    public void answerInlineQuery(String inlineQueryId, String resultsJson, int cacheTimeSeconds) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            telegramBotApiClient.answerInlineQuery(inlineQueryId, resultsJson, cacheTimeSeconds);
            sample.stop(Timer.builder("telegram.answer_inline_query.duration")
                    .tag("source", "telegram")
                    .tag("result", "success")
                    .register(meterRegistry));
        } catch (RuntimeException runtimeException) {
            sample.stop(Timer.builder("telegram.answer_inline_query.duration")
                    .tag("source", "telegram")
                    .tag("result", "error")
                    .register(meterRegistry));
            throw runtimeException;
        }
    }

    public TelegramMessageResponse sendDocument(Long chatId, File file, String caption) {
        long startedAt = System.currentTimeMillis();
        TelegramMessageResponse response = retryExecutor.execute("telegram.sendDocument", () -> telegramBotApiClient.sendDocument(chatId, file, caption));
        long durationMs = System.currentTimeMillis() - startedAt;
        log.info("Telegram upload completed: fileName={}, durationMs={}", file.getName(), durationMs);
        return response;
    }

    public String getMe() {
        return retryExecutor.execute("telegram.getMe", telegramBotApiClient::getMe);
    }

    public void setMyCommands(String commandsJson) {
        retryExecutor.executeVoid("telegram.setMyCommands", () -> telegramBotApiClient.setMyCommands(commandsJson));
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
