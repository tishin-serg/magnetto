package ru.xataaa.torrentbot.downloadlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.config.HomeWebdavProperties;
import ru.xataaa.torrentbot.media.HomeWebdavMediaLibraryService;
import ru.xataaa.torrentbot.regression.ApiRegression;

@ApiRegression
class HomeDownloadControllerApiRegressionTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldProxyRangeAndBasicAuthToHomeWebdav() throws Exception {
        AtomicReference<String> range = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/media/Movie.mkv", exchange -> {
            range.set(exchange.getRequestHeaders().getFirst(HttpHeaders.RANGE));
            authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            byte[] body = "data".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_RANGE, "bytes 0-3/100");
            exchange.sendResponseHeaders(206, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        HomeDownloadLinkService linkService = mock(HomeDownloadLinkService.class);
        HomeWebdavMediaLibraryService libraryService = mock(HomeWebdavMediaLibraryService.class);
        HomeDownloadLink link = HomeDownloadLink.builder()
                .id(UUID.randomUUID())
                .token("token")
                .fileName("folder/Movie.mkv")
                .status(DownloadLinkStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(linkService.getValidLink("token")).thenReturn(Optional.of(link));
        when(libraryService.tailscaleFileUrl("folder/Movie.mkv"))
                .thenReturn("http://127.0.0.1:" + server.getAddress().getPort() + "/media/Movie.mkv");
        HomeDownloadController controller = new HomeDownloadController(
                linkService,
                libraryService,
                new HomeWebdavProperties(true, "http://home", "http://lan", "user", "pass", 500, 2_000),
                WebClient.builder()
        );

        var response = controller.download("token", "bytes=0-3").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-3/100");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("Movie.mkv");
        assertThat(range.get()).isEqualTo("bytes=0-3");
        assertThat(authorization.get()).isEqualTo("Basic dXNlcjpwYXNz");
        if (response.getBody() != null) {
            response.getBody().doOnNext(DataBufferUtils::release).blockLast();
        }
    }

    @Test
    void shouldReturnNotFoundWithoutCallingHomeWebdavForInvalidToken() {
        HomeDownloadLinkService linkService = mock(HomeDownloadLinkService.class);
        when(linkService.getValidLink("missing")).thenReturn(Optional.empty());
        HomeDownloadController controller = new HomeDownloadController(
                linkService,
                mock(HomeWebdavMediaLibraryService.class),
                new HomeWebdavProperties(true, "http://home", "http://lan", "user", "pass", 500, 2_000),
                WebClient.builder()
        );

        var response = controller.download("missing", null).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
