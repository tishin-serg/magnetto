package ru.xataaa.torrentbot.telegram;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.telegram.dto.TelegramUpdate;

@Slf4j
@Service
public class TelegramPollingService {

    private final TelegramBotApiClient telegramBotApiClient;
    private final TelegramPollingStateRepository telegramPollingStateRepository;
    private final TelegramLlmCommandRouter telegramLlmCommandRouter;
    private final TelegramCallbackRouter telegramCallbackRouter;
    private final TelegramInlineQueryRouter telegramInlineQueryRouter;
    private final Executor telegramWorkExecutor;
    @org.springframework.beans.factory.annotation.Autowired
    private MovieSelectionCallbackHandler movieSelections;
    @org.springframework.beans.factory.annotation.Autowired
    private TelegramMessageService messages;

    public TelegramPollingService(
            TelegramBotApiClient telegramBotApiClient,
            TelegramPollingStateRepository telegramPollingStateRepository,
            TelegramLlmCommandRouter telegramLlmCommandRouter,
            TelegramCallbackRouter telegramCallbackRouter,
            TelegramInlineQueryRouter telegramInlineQueryRouter,
            @Qualifier("telegramWorkExecutor") Executor telegramWorkExecutor
    ) {
        this.telegramBotApiClient = telegramBotApiClient;
        this.telegramPollingStateRepository = telegramPollingStateRepository;
        this.telegramLlmCommandRouter = telegramLlmCommandRouter;
        this.telegramCallbackRouter = telegramCallbackRouter;
        this.telegramInlineQueryRouter = telegramInlineQueryRouter;
        this.telegramWorkExecutor = telegramWorkExecutor;
    }

    @Scheduled(fixedDelay = 1000)
    public void pollUpdates() {
        try {
            Long offset = telegramPollingStateRepository.getOffset().orElse(null);
            List<TelegramUpdate> updates = telegramBotApiClient.getUpdates(offset);
            updates.stream()
                    .sorted(Comparator.comparing(TelegramUpdate::getUpdateId))
                    .forEach(this::processUpdate);
        } catch (RuntimeException runtimeException) {
            log.warn("Telegram polling failed: error={}", runtimeException.getMessage());
        }
    }

    private void processUpdate(TelegramUpdate telegramUpdate) {
        try {
            if (telegramUpdate.getInlineQuery() != null
                    && telegramUpdate.getInlineQuery().getId() != null) {
                Long userId = telegramUpdate.getInlineQuery().getFrom() == null ? null : telegramUpdate.getInlineQuery().getFrom().getId();
                telegramInlineQueryRouter.route(
                        telegramUpdate.getInlineQuery().getId(),
                        userId,
                        telegramUpdate.getInlineQuery().getQuery()
                );
                return;
            }
            if (telegramUpdate.getChosenInlineResult() != null && movieSelections != null) {
                var chosen = telegramUpdate.getChosenInlineResult();
                if (chosen.getFrom() != null)
                    dispatchAsync(telegramUpdate, () -> movieSelections.openFromInline(chosen.getFrom().getId(), chosen.getResultId()));
                return;
            }
            if (telegramUpdate.getMessage() != null
                    && telegramUpdate.getMessage().getChat() != null
                    && telegramUpdate.getMessage().getText() != null) {
                var message = telegramUpdate.getMessage();
                if (message.getViaBot() != null && message.getReplyMarkup() != null && movieSelections != null) {
                    String callback = message.getReplyMarkup().path("inline_keyboard").path(0).path(0).path("callback_data").asText();
                    if (callback.startsWith("movie:open:")) {
                        dispatchAsync(telegramUpdate, () -> movieSelections.openFromInline(message.getChat().getId(), callback.substring("movie:open:".length())));
                        return;
                    }
                }
                dispatchAsync(telegramUpdate, () -> telegramLlmCommandRouter.route(
                        telegramUpdate.getMessage().getChat().getId(),
                        telegramUpdate.getMessage().getText()
                ));
                return;
            }
            if (telegramUpdate.getCallbackQuery() != null
                    && telegramUpdate.getCallbackQuery().getData() != null) {
                if (telegramUpdate.getCallbackQuery().getInlineMessageId() != null) {
                    Long userId = telegramUpdate.getCallbackQuery().getFrom() == null ? null : telegramUpdate.getCallbackQuery().getFrom().getId();
                    dispatchAsync(telegramUpdate, () -> telegramCallbackRouter.routeInline(
                            telegramUpdate.getCallbackQuery().getId(),
                            telegramUpdate.getCallbackQuery().getInlineMessageId(),
                            userId,
                            telegramUpdate.getCallbackQuery().getData()
                    ));
                    return;
                }
                if (telegramUpdate.getCallbackQuery().getMessage() == null
                        || telegramUpdate.getCallbackQuery().getMessage().getChat() == null) {
                    return;
                }
                dispatchAsync(telegramUpdate, () -> telegramCallbackRouter.route(
                        telegramUpdate.getCallbackQuery().getId(),
                        telegramUpdate.getCallbackQuery().getMessage().getChat().getId(),
                        telegramUpdate.getCallbackQuery().getMessage().getMessageId(),
                        telegramUpdate.getCallbackQuery().getData()
                ));
            }
        } finally {
            telegramPollingStateRepository.saveOffset(telegramUpdate.getUpdateId() + 1);
        }
    }

    private void dispatchAsync(TelegramUpdate telegramUpdate, Runnable runnable) {
        telegramWorkExecutor.execute(() -> {
            try {
                runnable.run();
            } catch (RuntimeException runtimeException) {
                log.warn("Telegram update processing failed: updateId={}, error={}",
                        telegramUpdate.getUpdateId(), runtimeException.getMessage());
                if (messages != null && telegramUpdate.getMessage() != null && telegramUpdate.getMessage().getChat() != null) {
                    try {
                        messages.sendTextWithInlineKeyboard(telegramUpdate.getMessage().getChat().getId(),
                                "Не удалось обработать сообщение. Открой нужный раздел заново. Если запускалась загрузка, сначала проверь её состояние.",
                                new TelegramKeyboardFactory().mainMenuKeyboard());
                    } catch (RuntimeException deliveryFailure) {
                        // A blocked bot or unavailable Telegram cannot receive recovery messages; /start restores the UI.
                        log.debug("Recovery message could not be delivered");
                    }
                }
            }
        });
    }
}
