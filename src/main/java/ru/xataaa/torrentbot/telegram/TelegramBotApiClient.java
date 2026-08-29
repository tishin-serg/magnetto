package ru.xataaa.torrentbot.telegram;

import java.io.File;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.config.TelegramProperties;
import ru.xataaa.torrentbot.config.WebClientConfig;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.retry.RetryableOperationException;
import ru.xataaa.torrentbot.telegram.dto.TelegramApiResponse;
import ru.xataaa.torrentbot.telegram.dto.TelegramMessageResponse;
import ru.xataaa.torrentbot.telegram.dto.TelegramUpdate;

@Slf4j
@Component
public class TelegramBotApiClient {

    private final TelegramProperties telegramProperties;
    private final WebClient webClient;

    public TelegramBotApiClient(TelegramProperties telegramProperties, WebClient.Builder webClientBuilder) {
        this.telegramProperties = telegramProperties;
        this.webClient = webClientBuilder
                .baseUrl(telegramProperties.botApiBaseUrl())
                .clientConnector(WebClientConfig.connector(telegramProperties.connectTimeoutMs(), telegramProperties.requestTimeoutMs()))
                .build();
    }

    public List<TelegramUpdate> getUpdates(Long offset) {
        TelegramApiResponse<List<TelegramUpdate>> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/getUpdates")
                        .queryParamIfPresent("offset", java.util.Optional.ofNullable(offset))
                        .queryParam("timeout", 20)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramApiResponse<List<TelegramUpdate>>>() {
                })
                .doOnError(WebClientResponseException.class, this::logTelegramHttpError)
                .onErrorMap(WebClientResponseException.class, exception -> toTelegramException(exception, ErrorCode.TELEGRAM_SEND_FAILED))
                .block(Duration.ofMillis(telegramProperties.requestTimeoutMs() + 5000L));
        if (response == null || !response.isOk()) {
            throw new RetryableOperationException(ErrorCode.TELEGRAM_SEND_FAILED, "Telegram getUpdates failed");
        }
        return response.getResult();
    }

    public TelegramMessageResponse sendMessage(Long chatId, String text) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("chat_id", chatId.toString());
        formData.add("text", text);
        return sendMessageForm(formData);
    }

    public TelegramMessageResponse sendMessageWithReplyMarkup(Long chatId, String text, String replyMarkupJson) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("chat_id", chatId.toString());
        formData.add("text", text);
        formData.add("reply_markup", replyMarkupJson);
        return sendMessageForm(formData);
    }

    public TelegramMessageResponse editMessageText(Long chatId, Long messageId, String text, String replyMarkupJson) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("chat_id", chatId.toString());
        formData.add("message_id", messageId.toString());
        formData.add("text", text);
        if (replyMarkupJson != null && !replyMarkupJson.isBlank()) {
            formData.add("reply_markup", replyMarkupJson);
        }
        TelegramApiResponse<TelegramMessageResponse> response;
        try {
            response = webClient.post()
                    .uri("/editMessageText")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<TelegramApiResponse<TelegramMessageResponse>>() {
                    })
                    .block(Duration.ofMillis(telegramProperties.requestTimeoutMs()));
        } catch (WebClientResponseException webClientResponseException) {
            if (isMessageNotModified(webClientResponseException)) {
                log.info("Telegram edit skipped: message is not modified, chatId={}, messageId={}", chatId, messageId);
                TelegramMessageResponse messageResponse = new TelegramMessageResponse();
                messageResponse.setMessageId(messageId);
                return messageResponse;
            }
            logTelegramHttpError(webClientResponseException);
            throw toTelegramException(webClientResponseException, ErrorCode.TELEGRAM_SEND_FAILED);
        }
        return requireOk(response, ErrorCode.TELEGRAM_SEND_FAILED);
    }

    public void answerCallbackQuery(String callbackQueryId, String text) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("callback_query_id", callbackQueryId);
        if (text != null && !text.isBlank()) {
            formData.add("text", text);
        }
        TelegramApiResponse<Object> response = webClient.post()
                .uri("/answerCallbackQuery")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramApiResponse<Object>>() {
                })
                .doOnError(WebClientResponseException.class, this::logTelegramHttpError)
                .onErrorMap(WebClientResponseException.class, exception -> toTelegramException(exception, ErrorCode.TELEGRAM_SEND_FAILED))
                .block(Duration.ofMillis(telegramProperties.requestTimeoutMs()));
        if (response == null || !response.isOk()) {
            throw new RetryableOperationException(ErrorCode.TELEGRAM_SEND_FAILED, "Telegram answerCallbackQuery failed");
        }
    }

    public void answerInlineQuery(String inlineQueryId, String resultsJson, int cacheTimeSeconds) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("inline_query_id", inlineQueryId);
        formData.add("results", resultsJson);
        formData.add("cache_time", String.valueOf(cacheTimeSeconds));
        formData.add("is_personal", "true");
        TelegramApiResponse<Object> response = webClient.post()
                .uri("/answerInlineQuery")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramApiResponse<Object>>() {
                })
                .doOnError(WebClientResponseException.class, this::logTelegramHttpError)
                .onErrorMap(WebClientResponseException.class, exception -> toTelegramException(exception, ErrorCode.TELEGRAM_SEND_FAILED))
                .block(Duration.ofMillis(telegramProperties.requestTimeoutMs()));
        if (response == null || !response.isOk()) {
            throw new RetryableOperationException(ErrorCode.TELEGRAM_SEND_FAILED, "Telegram answerInlineQuery failed");
        }
    }

    private TelegramMessageResponse sendMessageForm(MultiValueMap<String, String> formData) {
        TelegramApiResponse<TelegramMessageResponse> response = webClient.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramApiResponse<TelegramMessageResponse>>() {
                })
                .doOnError(WebClientResponseException.class, this::logTelegramHttpError)
                .onErrorMap(WebClientResponseException.class, exception -> toTelegramException(exception, ErrorCode.TELEGRAM_SEND_FAILED))
                .block(Duration.ofMillis(telegramProperties.requestTimeoutMs()));
        return requireOk(response, ErrorCode.TELEGRAM_SEND_FAILED);
    }

    public TelegramMessageResponse sendDocument(Long chatId, File file, String caption) {
        MultiValueMap<String, Object> multipartData = new LinkedMultiValueMap<>();
        multipartData.add("chat_id", chatId.toString());
        multipartData.add("document", new FileSystemResource(file));
        if (caption != null && !caption.isBlank()) {
            multipartData.add("caption", caption);
        }
        TelegramApiResponse<TelegramMessageResponse> response = webClient.post()
                .uri("/sendDocument")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartData))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramApiResponse<TelegramMessageResponse>>() {
                })
                .doOnError(WebClientResponseException.class, this::logTelegramHttpError)
                .onErrorMap(WebClientResponseException.class, exception -> toTelegramException(exception, ErrorCode.TELEGRAM_UPLOAD_FAILED))
                .block(Duration.ofMillis(telegramProperties.uploadTimeoutMs()));
        return requireOk(response, ErrorCode.TELEGRAM_UPLOAD_FAILED);
    }

    public String getMe() {
        TelegramApiResponse<Object> response = webClient.get()
                .uri("/getMe")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramApiResponse<Object>>() {
                })
                .doOnError(WebClientResponseException.class, this::logTelegramHttpError)
                .onErrorMap(WebClientResponseException.class, exception -> toTelegramException(exception, ErrorCode.TELEGRAM_SEND_FAILED))
                .block(Duration.ofMillis(telegramProperties.requestTimeoutMs()));
        if (response == null || !response.isOk()) {
            throw new RetryableOperationException(ErrorCode.TELEGRAM_SEND_FAILED, "Telegram getMe failed");
        }
        return String.valueOf(response.getResult());
    }

    public void setMyCommands(String commandsJson) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("commands", commandsJson);
        TelegramApiResponse<Object> response = webClient.post()
                .uri("/setMyCommands")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramApiResponse<Object>>() {
                })
                .doOnError(WebClientResponseException.class, this::logTelegramHttpError)
                .onErrorMap(WebClientResponseException.class, exception -> toTelegramException(exception, ErrorCode.TELEGRAM_SEND_FAILED))
                .block(Duration.ofMillis(telegramProperties.requestTimeoutMs()));
        if (response == null || !response.isOk()) {
            throw new RetryableOperationException(ErrorCode.TELEGRAM_SEND_FAILED, "Telegram setMyCommands failed");
        }
    }

    private TelegramMessageResponse requireOk(TelegramApiResponse<TelegramMessageResponse> response, ErrorCode errorCode) {
        if (response == null) {
            throw new RetryableOperationException(errorCode, "Telegram API returned empty response");
        }
        if (!response.isOk()) {
            if (response.getErrorCode() != null && response.getErrorCode() >= 400 && response.getErrorCode() < 500) {
                throw new NonRetryableOperationException(errorCode, response.getDescription());
            }
            throw new RetryableOperationException(errorCode, response.getDescription());
        }
        return response.getResult();
    }

    private void logTelegramHttpError(WebClientResponseException exception) {
        log.warn(
                "Telegram HTTP error: status={}, responseBody={}",
                exception.getStatusCode(),
                shorten(exception.getResponseBodyAsString(), 500)
        );
    }

    private RuntimeException toTelegramException(WebClientResponseException exception, ErrorCode errorCode) {
        String message = "Telegram API HTTP error: status=" + exception.getStatusCode()
                + ", responseBody=" + shorten(exception.getResponseBodyAsString(), 500);
        if (exception.getStatusCode().is4xxClientError()) {
            return new NonRetryableOperationException(errorCode, message, exception);
        }
        return new RetryableOperationException(errorCode, message, exception);
    }

    private boolean isMessageNotModified(WebClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        return exception.getStatusCode().value() == 400
                && responseBody != null
                && responseBody.contains("message is not modified");
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
