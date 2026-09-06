package ru.xataaa.torrentbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.config.TelegramProperties;
import ru.xataaa.torrentbot.regression.ApiRegression;

@ApiRegression
class TelegramBotApiClientApiRegressionTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldPollAllSupportedUpdateTypesAndSendInlineKeyboard() throws Exception {
        AtomicReference<String> updatesQuery = new AtomicReference<>();
        AtomicReference<String> messageBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bottest-token/getUpdates", exchange -> {
            updatesQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, "{\"ok\":true,\"result\":[]}");
        });
        server.createContext("/bottest-token/sendMessage", exchange -> {
            messageBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"ok\":true,\"result\":{\"message_id\":77}}");
        });
        server.start();
        TelegramBotApiClient client = new TelegramBotApiClient(properties(), WebClient.builder());

        assertThat(client.getUpdates(123L)).isEmpty();
        var response = client.sendMessageWithReplyMarkup(
                42L,
                "Choose",
                "{\"inline_keyboard\":[[{\"text\":\"OK\",\"callback_data\":\"ok\"}]]}"
        );

        assertThat(response.getMessageId()).isEqualTo(77L);
        assertThat(updatesQuery.get()).contains(
                "offset=123",
                "timeout=20",
                "message",
                "callback_query",
                "inline_query",
                "chosen_inline_result"
        );
        assertThat(messageBody.get()).contains(
                "chat_id=42",
                "text=Choose",
                "reply_markup="
        );
    }

    private TelegramProperties properties() {
        return new TelegramProperties(
                "test-token",
                "test-bot",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                500,
                2_000,
                2_000,
                new TelegramProperties.FileProperties(1000, 24, true)
        );
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws java.io.IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
