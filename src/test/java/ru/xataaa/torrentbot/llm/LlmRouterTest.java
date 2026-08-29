package ru.xataaa.torrentbot.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LlmRouterTest {

    private final FakeOllamaChatClient ollamaChatClient = new FakeOllamaChatClient();
    private final LlmRouter router = new LlmRouter(
            ollamaChatClient,
            new ObjectMapper(),
            new BotScenarioCatalog()
    );

    @Test
    void shouldParseShowTasks() {
        ollamaChatClient.response = json("show_tasks", 0.98, args(null, null, null, null, null, null, null, null, null, null), null);

        LlmRouteResult result = router.route(1L, "покажи задачи", UserDialogState.empty(1L));

        assertThat(result.action()).isEqualTo(LlmAction.SHOW_TASKS);
        assertThat(result.arguments()).containsEntry("title", null);
    }

    @Test
    void shouldParseFreeSpace() {
        ollamaChatClient.response = json("show_free_space", 0.98, args(null, null, null, null, null, null, null, null, null, null), null);

        LlmRouteResult result = router.route(1L, "сколько места осталось", UserDialogState.empty(1L));

        assertThat(result.action()).isEqualTo(LlmAction.SHOW_FREE_SPACE);
        assertThat(result.confidence()).isEqualTo(0.98);
    }

    @Test
    void shouldParseSearchMovieByTitleQualitySourceAndAudio() {
        ollamaChatClient.response = json(
                "search_media",
                0.95,
                args("Big Buck Bunny", "movie", null, null, "2160", "bdremux", "дуб", null, null, null),
                "Ищу подходящие варианты..."
        );

        LlmRouteResult result = router.route(1L, "найди Big Buck Bunny bdremux 2160 с дубляжом", UserDialogState.empty(1L));

        assertThat(result.action()).isEqualTo(LlmAction.SEARCH_MEDIA);
        assertThat(result.arguments()).containsEntry("title", "Big Buck Bunny");
        assertThat(result.arguments()).containsEntry("media_type", "movie");
        assertThat(result.arguments()).containsEntry("quality", "2160p");
        assertThat(result.arguments()).containsEntry("source", "BDRemux");
        assertThat(result.arguments()).containsEntry("audio", "Дубляж");
    }

    @Test
    void shouldParseSearchSeriesWithSeason() {
        ollamaChatClient.response = json(
                "search_media",
                0.95,
                args("Настоящий детектив", "series", 2, null, "1080", null, null, null, null, null),
                "Ищу подходящие варианты..."
        );

        LlmRouteResult result = router.route(1L, "второй сезон настоящего детектива в 1080", UserDialogState.empty(1L));

        assertThat(result.action()).isEqualTo(LlmAction.SEARCH_MEDIA);
        assertThat(result.arguments()).containsEntry("title", "Настоящий детектив");
        assertThat(result.arguments()).containsEntry("media_type", "series");
        assertThat(result.arguments()).containsEntry("season", 2);
        assertThat(result.arguments()).containsEntry("quality", "1080p");
    }

    @Test
    void shouldParsePauseTask() {
        ollamaChatClient.response = json("pause_task", 0.95, args(null, null, null, null, null, null, null, null, null, "1"), null);

        LlmRouteResult result = router.route(1L, "поставь первую загрузку на паузу", UserDialogState.empty(1L));

        assertThat(result.action()).isEqualTo(LlmAction.PAUSE_TASK);
        assertThat(result.arguments()).containsEntry("task_id", "1");
    }

    @Test
    void shouldParseResumeTask() {
        ollamaChatClient.response = json("resume_task", 0.95, args(null, null, null, null, null, null, null, null, null, "2"), null);

        LlmRouteResult result = router.route(1L, "продолжи задачу 2", UserDialogState.empty(1L));

        assertThat(result.action()).isEqualTo(LlmAction.RESUME_TASK);
        assertThat(result.arguments()).containsEntry("task_id", "2");
    }

    @Test
    void shouldParseHomeLibrary() {
        ollamaChatClient.response = json("show_library", 0.95, args(null, null, null, null, null, null, null, null, "home", null), null);

        LlmRouteResult result = router.route(1L, "открой домашнюю медиатеку", UserDialogState.empty(1L));

        assertThat(result.action()).isEqualTo(LlmAction.SHOW_LIBRARY);
        assertThat(result.arguments()).containsEntry("destination", "home_pc");
    }

    @Test
    void shouldParseIphoneHelp() {
        ollamaChatClient.response = json("show_iphone_help", 0.95, args(null, null, null, null, null, null, null, null, null, null), null);

        LlmRouteResult result = router.route(1L, "как смотреть на айфоне через infuse", UserDialogState.empty(1L));

        assertThat(result.action()).isEqualTo(LlmAction.SHOW_IPHONE_HELP);
    }

    @Test
    void shouldFallbackOnBrokenJson() {
        LlmRouteResult result = router.parseAndNormalize("not json");

        assertThat(result.action()).isEqualTo(LlmAction.UNKNOWN);
        assertThat(result.confidence()).isZero();
    }

    @Test
    void shouldFallbackOnUnknownAction() {
        LlmRouteResult result = router.parseAndNormalize(json("delete_everything", 0.99, args(null, null, null, null, null, null, null, null, null, null), null));

        assertThat(result.action()).isEqualTo(LlmAction.UNKNOWN);
        assertThat(router.requiresFallback(result, 0.6)).isTrue();
    }

    private String json(String action, double confidence, String arguments, String reply) {
        return "{"
                + "\"action\":\"" + action + "\","
                + "\"confidence\":" + confidence + ","
                + "\"arguments\":" + arguments + ","
                + "\"reply\":" + value(reply)
                + "}";
    }

    private String args(
            String title,
            String mediaType,
            Integer season,
            String episodes,
            String quality,
            String source,
            String audio,
            String language,
            String destination,
            String taskId
    ) {
        return "{"
                + "\"title\":" + value(title) + ","
                + "\"media_type\":" + value(mediaType) + ","
                + "\"season\":" + (season == null ? "null" : season) + ","
                + "\"episodes\":" + value(episodes) + ","
                + "\"quality\":" + value(quality) + ","
                + "\"source\":" + value(source) + ","
                + "\"audio\":" + value(audio) + ","
                + "\"language\":" + value(language) + ","
                + "\"destination\":" + value(destination) + ","
                + "\"task_id\":" + value(taskId)
                + "}";
    }

    private String value(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static class FakeOllamaChatClient implements OllamaChatClient {
        private String response = "{}";

        @Override
        public String chat(String systemPrompt, String userPrompt) {
            assertThat(systemPrompt).contains("intent router");
            assertThat(userPrompt).contains("available_actions");
            return response;
        }
    }
}
