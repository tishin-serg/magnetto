package ru.xataaa.torrentbot.downloadlink;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.xataaa.torrentbot.config.HomeWebdavProperties;
import ru.xataaa.torrentbot.config.WebClientConfig;
import ru.xataaa.torrentbot.media.HomeWebdavMediaLibraryService;

@RestController
@RequiredArgsConstructor
public class HomeDownloadController {

    private final HomeDownloadLinkService homeDownloadLinkService;
    private final HomeWebdavMediaLibraryService homeWebdavMediaLibraryService;
    private final HomeWebdavProperties homeWebdavProperties;
    private final WebClient.Builder webClientBuilder;

    @GetMapping("/home-download/{token}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> download(
            @PathVariable String token,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        return Mono.fromCallable(() -> homeDownloadLinkService.getValidLink(token))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalLink -> optionalLink
                        .map(link -> proxyHomeFile(link, rangeHeader))
                        .orElseGet(() -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build())));
    }

    private Mono<ResponseEntity<Flux<DataBuffer>>> proxyHomeFile(HomeDownloadLink homeDownloadLink, String rangeHeader) {
        WebClient.RequestHeadersSpec<?> request = webClient().get()
                .uri(homeWebdavMediaLibraryService.tailscaleFileUrl(homeDownloadLink.getFileName()))
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader());
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            request = request.header(HttpHeaders.RANGE, rangeHeader);
        }
        return request.retrieve()
                .toEntityFlux(DataBuffer.class)
                .map(responseEntity -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
                    copyHeader(responseEntity.getHeaders(), headers, HttpHeaders.CONTENT_LENGTH);
                    copyHeader(responseEntity.getHeaders(), headers, HttpHeaders.CONTENT_RANGE);
                    headers.setContentDisposition(ContentDisposition.attachment()
                            .filename(downloadFileName(homeDownloadLink.getFileName()), StandardCharsets.UTF_8)
                            .build());
                    return ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(headers)
                            .body(responseEntity.getBody());
                })
                .onErrorResume(WebClientResponseException.class,
                        exception -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }

    private WebClient webClient() {
        return webClientBuilder
                .clientConnector(WebClientConfig.connector(homeWebdavProperties.connectTimeoutMs(), homeWebdavProperties.requestTimeoutMs()))
                .build();
    }

    private String authorizationHeader() {
        String credentials = homeWebdavProperties.username() + ":" + homeWebdavProperties.password();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private void copyHeader(HttpHeaders sourceHeaders, HttpHeaders targetHeaders, String headerName) {
        String value = sourceHeaders.getFirst(headerName);
        if (value != null && !value.isBlank()) {
            targetHeaders.set(headerName, value);
        }
    }

    private String downloadFileName(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "download.bin";
        }
        int lastSlashIndex = relativePath.lastIndexOf('/');
        if (lastSlashIndex < 0 || lastSlashIndex + 1 >= relativePath.length()) {
            return relativePath;
        }
        return relativePath.substring(lastSlashIndex + 1);
    }
}
