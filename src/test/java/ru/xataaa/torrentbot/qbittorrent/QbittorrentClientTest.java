package ru.xataaa.torrentbot.qbittorrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.config.QbittorrentProperties;
import ru.xataaa.torrentbot.job.DownloadTarget;

class QbittorrentClientTest {

    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldFallbackResumeToStartWhenQbittorrentReturns404() throws Exception {
        startServerWithFallback("/api/v2/torrents/resume", "/api/v2/torrents/start");

        client().resumeTorrent(DownloadTarget.VPS, "abc");

        assertThat(requestedPaths).containsExactly("/api/v2/torrents/resume", "/api/v2/torrents/start");
    }

    @Test
    void shouldFallbackPauseToStopWhenQbittorrentReturns404() throws Exception {
        startServerWithFallback("/api/v2/torrents/pause", "/api/v2/torrents/stop");

        client().pauseTorrent(DownloadTarget.VPS, "abc");

        assertThat(requestedPaths).containsExactly("/api/v2/torrents/pause", "/api/v2/torrents/stop");
    }

    private void startServerWithFallback(String primaryPath, String fallbackPath) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            byte[] response = new byte[0];
            int status = exchange.getRequestURI().getPath().equals(primaryPath) ? 404 : 200;
            if (!exchange.getRequestURI().getPath().equals(primaryPath)
                    && !exchange.getRequestURI().getPath().equals(fallbackPath)) {
                status = 500;
            }
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        server.start();
    }

    private QbittorrentClient client() {
        QbittorrentAuthService authService = mock(QbittorrentAuthService.class);
        when(authService.getSessionCookie(DownloadTarget.VPS)).thenReturn("SID=test");
        return new QbittorrentClient(properties(), authService, WebClient.builder());
    }

    private QbittorrentProperties properties() {
        return new QbittorrentProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "admin",
                "password",
                "/downloads",
                1000,
                2000,
                null,
                null
        );
    }
}
