package ru.xataaa.torrentbot.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.xataaa.torrentbot.config.HomeWebdavProperties;

class HomeWebdavCleanupServiceTest {

    @Test
    void shouldDeleteEncodedPathWithoutDoubleEncoding() {
        List<String> paths = new ArrayList<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            paths.add(request.url().getRawPath());
            return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        });
        HomeWebdavCleanupService service = new HomeWebdavCleanupService(
                new HomeWebdavProperties(true, "http://webdav.example/", "", "user", "password", 1000, 1000),
                builder);

        service.deleteFile("Top Gear & Фильм/movie file.mkv");

        assertThat(paths).containsExactly("/Top%20Gear%20%26%20%D0%A4%D0%B8%D0%BB%D1%8C%D0%BC/movie%20file.mkv");
        assertThat(paths).noneMatch(path -> path.contains("%25"));
    }
}
