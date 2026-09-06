package ru.xataaa.torrentbot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUpdate {
    @JsonProperty("update_id")
    private Long updateId;
    private TelegramMessage message;
    @JsonProperty("callback_query")
    private TelegramCallbackQuery callbackQuery;
    @JsonProperty("inline_query")
    private TelegramInlineQuery inlineQuery;
    @JsonProperty("chosen_inline_result")
    private TelegramChosenInlineResult chosenInlineResult;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramMessage {
        @JsonProperty("message_id")
        private Long messageId;
        private TelegramChat chat;
        private String text;
        @JsonProperty("reply_markup")
        private com.fasterxml.jackson.databind.JsonNode replyMarkup;
        @JsonProperty("via_bot")
        private TelegramUser viaBot;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramCallbackQuery {
        private String id;
        private TelegramUser from;
        private TelegramMessage message;
        @JsonProperty("inline_message_id")
        private String inlineMessageId;
        private String data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramInlineQuery {
        private String id;
        private TelegramUser from;
        private String query;
        private String offset;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramChosenInlineResult {
        @JsonProperty("result_id")
        private String resultId;
        private TelegramUser from;
        private String query;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramUser {
        private Long id;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramChat {
        private Long id;
    }
}
