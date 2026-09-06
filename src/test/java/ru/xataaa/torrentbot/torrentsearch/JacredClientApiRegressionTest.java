package ru.xataaa.torrentbot.torrentsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.config.JacredProperties;
import ru.xataaa.torrentbot.regression.ApiRegression;

@ApiRegression
class JacredClientApiRegressionTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendStructuredSearchContractAndDecodeResults() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2.0/indexers/all/results", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = """
                    {"Results":[{"Tracker":"rutracker","Title":"The Matrix 1999","MagnetUri":"magnet:?xt=urn:btih:abc","Size":1000,"Seeders":42,"Peers":3}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        JacredClient client = client();

        var results = client.search(TorrentSearchRequest.fromMovie("Matrix", "The Matrix", 1999, false), false);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getTitle()).isEqualTo("The Matrix 1999");
        assertThat(results.getFirst().getSeeders()).isEqualTo(42);
        assertThat(rawQuery.get()).contains(
                "apikey=test-key",
                "title=Matrix",
                "title_original=The%20Matrix",
                "year=1999",
                "is_serial=1"
        );
    }

    @Test
    void shouldUseQueryOnlyForFallbackSearch() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2.0/indexers/all/results", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "{\"Results\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        JacredClient client = client();

        client.search(TorrentSearchRequest.fromUserText("Matrix 1999"), true);

        assertThat(rawQuery.get()).contains("apikey=test-key", "Query=Matrix%201999");
        assertThat(rawQuery.get()).doesNotContain("title=", "year=", "is_serial=");
    }

    private JacredClient client() {
        return new JacredClient(
                new JacredProperties(
                        "http://127.0.0.1:" + server.getAddress().getPort(),
                        "test-key",
                        500,
                        2_000,
                        2_000,
                        50
                ),
                WebClient.builder()
        );
    }
}
