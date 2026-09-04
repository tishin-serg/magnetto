package ru.xataaa.torrentbot.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.config.TmdbProperties;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

class TmdbClientTest {

    @Test
    void shouldEnforceSearchRequestTimeoutAtClientBoundary() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search/multi", exchange -> {
            try {
                Thread.sleep(2_000);
                byte[] body = "{\"results\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            TmdbClient client = new TmdbClient(
                    new TmdbProperties("key", "http://127.0.0.1:" + server.getAddress().getPort(), "http://img", "ru-RU", 100, 200, 60, 1440, 10),
                    new SimpleMeterRegistry(),
                    new ObjectMapper()
            );

            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> client.searchMulti("Matrix"))
                    .isInstanceOf(RetryableOperationException.class);
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

            assertThat(elapsedMs).isLessThan(1_500L);
        } finally {
            server.stop(0);
        }
    }
}
