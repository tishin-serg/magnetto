package ru.xataaa.torrentbot.media;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.config.HomeWebdavProperties;
import ru.xataaa.torrentbot.config.WebClientConfig;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Service
@RequiredArgsConstructor
public class HomeWebdavCleanupService {

    private static final Pattern HREF_PATTERN = Pattern.compile("<(?:d:)?href>(.*?)</(?:d:)?href>", Pattern.CASE_INSENSITIVE);

    private final HomeWebdavProperties homeWebdavProperties;
    private final WebClient.Builder webClientBuilder;

    public MediaLibraryCleanupResult cleanupAllFiles() {
        if (!homeWebdavProperties.enabled()) {
            throw new NonRetryableOperationException(ErrorCode.CLEANUP_FAILED, "Home WebDAV is disabled");
        }
        String listingXml = listHomeDirectory();
        long deletedFiles = 0L;
        Matcher matcher = HREF_PATTERN.matcher(listingXml);
        while (matcher.find()) {
            String href = matcher.group(1);
            if (!isDeletableFileHref(href)) {
                continue;
            }
            deleteHref(href);
            deletedFiles++;
        }
        return new MediaLibraryCleanupResult(deletedFiles, 0L);
    }

    public void deleteFile(String relativePath) {
        if (!homeWebdavProperties.enabled()) {
            throw new NonRetryableOperationException(ErrorCode.CLEANUP_FAILED, "Home WebDAV is disabled");
        }
        if (!isSafeRelativePath(relativePath)) {
            throw new NonRetryableOperationException(ErrorCode.CLEANUP_FAILED, "Unsafe home WebDAV file name");
        }
        deleteHref("/" + encodeRelativePath(relativePath));
    }

    private String listHomeDirectory() {
        String responseBody = webClient().method(HttpMethod.valueOf("PROPFIND"))
                .uri("/")
                .header("Depth", "1")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                .retrieve()
                .bodyToMono(String.class)
                .onErrorMap(throwable -> new RetryableOperationException(ErrorCode.CLEANUP_FAILED, "Failed to list home WebDAV", throwable))
                .block(Duration.ofMillis(homeWebdavProperties.requestTimeoutMs()));
        return responseBody == null ? "" : responseBody;
    }

    private void deleteHref(String href) {
        webClient().delete()
                .uri(href)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                .retrieve()
                .toBodilessEntity()
                .onErrorMap(throwable -> new RetryableOperationException(ErrorCode.CLEANUP_FAILED, "Failed to delete home WebDAV file", throwable))
                .block(Duration.ofMillis(homeWebdavProperties.requestTimeoutMs()));
    }

    private boolean isDeletableFileHref(String href) {
        if (href == null || href.isBlank() || href.endsWith("/")) {
            return false;
        }
        return !href.contains("..");
    }

    private boolean isSafeRelativePath(String relativePath) {
        return relativePath != null
                && !relativePath.isBlank()
                && !relativePath.startsWith("/")
                && !relativePath.contains("..")
                && !relativePath.contains("\\");
    }

    private String encodeRelativePath(String relativePath) {
        String[] pathParts = relativePath.split("/");
        List<String> encodedPathParts = new ArrayList<>();
        for (String pathPart : pathParts) {
            if (pathPart.isBlank()) {
                continue;
            }
            encodedPathParts.add(URLEncoder.encode(pathPart, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("/", encodedPathParts);
    }

    private WebClient webClient() {
        return webClientBuilder
                .baseUrl(homeWebdavProperties.baseUrl())
                .clientConnector(WebClientConfig.connector(homeWebdavProperties.connectTimeoutMs(), homeWebdavProperties.requestTimeoutMs()))
                .build();
    }

    private String authorizationHeader() {
        String credentials = homeWebdavProperties.username() + ":" + homeWebdavProperties.password();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
