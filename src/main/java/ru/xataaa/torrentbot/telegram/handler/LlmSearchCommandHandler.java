package ru.xataaa.torrentbot.telegram.handler;

import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.config.LlmProperties;
import ru.xataaa.torrentbot.llm.LlmAction;
import ru.xataaa.torrentbot.llm.LlmRouteResult;
import ru.xataaa.torrentbot.llm.LlmRouter;
import ru.xataaa.torrentbot.llm.UserDialogStateRepository;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmSearchCommandHandler implements TelegramMessageHandler {

    private final LlmProperties llmProperties;
    private final LlmRouter llmRouter;
    private final UserDialogStateRepository userDialogStateRepository;
    private final TorrentSearchMessageHandler torrentSearchMessageHandler;
    private final HelpCommandHandler helpCommandHandler;
    private final TelegramMessageService telegramMessageService;

    @Override
    public boolean supports(String text) {
        return text != null && text.trim().startsWith("/llm");
    }

    @Override
    public void handle(Long chatId, String text) {
        if (!llmProperties.enabled()) {
            telegramMessageService.sendText(chatId, "LLM-router выключен. Включи LLM_ROUTER_ENABLED=true.");
            return;
        }
        String prompt = text == null ? "" : text.trim().replaceFirst("^/llm(@\\w+)?", "").trim();
        if (prompt.isBlank()) {
            telegramMessageService.sendText(chatId, "Напиши запрос после /llm. Например: /llm найди интерстеллар 2160 bdremux");
            return;
        }
        LlmRouteResult routeResult = llmRouter.route(chatId, prompt, userDialogStateRepository.find(chatId));
        if (llmRouter.requiresFallback(routeResult, llmProperties.minConfidence())) {
            log.info("llm_command_fallback: chatId={}, action={}, confidence={}",
                    chatId, routeResult.action().code(), routeResult.confidence());
            if (routeResult.reply() != null && !routeResult.reply().isBlank()) {
                telegramMessageService.sendText(chatId, routeResult.reply());
                return;
            }
            helpCommandHandler.handle(chatId, "/help");
            return;
        }
        if (routeResult.action() != LlmAction.SEARCH_MEDIA || !llmRouter.hasSearchTitle(routeResult)) {
            if (routeResult.reply() != null && !routeResult.reply().isBlank()) {
                telegramMessageService.sendText(chatId, routeResult.reply());
                return;
            }
            helpCommandHandler.handle(chatId, "/help");
            return;
        }
        userDialogStateRepository.save(llmRouter.updateState(chatId, routeResult));
        String query = searchQuery(routeResult.arguments());
        log.info("llm_command_search: chatId={}, queryPreview={}", chatId, queryPreview(query));
        torrentSearchMessageHandler.handle(chatId, query);
    }

    private String searchQuery(Map<String, Object> arguments) {
        String title = stringArg(arguments, "title");
        String suffix = arguments.entrySet().stream()
                .filter(entry -> !"title".equals(entry.getKey()) && !"media_type".equals(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.toString().isBlank())
                .map(Object::toString)
                .distinct()
                .collect(Collectors.joining(" "));
        return suffix.isBlank() ? title : title + " " + suffix;
    }

    private String queryPreview(String query) {
        if (query == null) {
            return "";
        }
        return query.length() > 80 ? query.substring(0, 77) + "..." : query;
    }

    private String stringArg(Map<String, Object> arguments, String key) {
        if (arguments == null || arguments.get(key) == null) {
            return "";
        }
        return arguments.get(key).toString().trim();
    }
}
