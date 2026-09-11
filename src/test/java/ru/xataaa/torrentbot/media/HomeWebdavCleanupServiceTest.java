package ru.xataaa.torrentbot.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.config.HomeWebdavProperties;
import ru.xataaa.torrentbot.regression.ApiRegression;
import reactor.core.publisher.Mono;

@ApiRegression
class HomeWebdavCleanupServiceTest {

    @Test
    void shouldNotDoubleEncodeFilePathWhenDeleting() {
        List<String> requestedPaths = new ArrayList<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requestedPaths.add(request.url().getRawPath());
            return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        });
        HomeWebdavCleanupService service = new HomeWebdavCleanupService(properties(), builder);

        service.deleteFile("Serial/Бриджертон S04 E01.mkv");

        assertThat(requestedPaths).containsExactly("/Serial/%D0%91%D1%80%D0%B8%D0%B4%D0%B6%D0%B5%D1%80%D1%82%D0%BE%D0%BD%20S04%20E01.mkv");
        assertThat(requestedPaths).noneMatch(path -> path.contains("%25"));
    }

    private HomeWebdavProperties properties() {
        return new HomeWebdavProperties(true, "http://home:8085/", "", "user", "password", 1000, 1000);
    }
}
