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
            if (telegramUpdate.getMessage() != null
                    && telegramUpdate.getMessage().getChat() != null
                    && telegramUpdate.getMessage().getText() != null) {
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
            }
        });
    }
}
