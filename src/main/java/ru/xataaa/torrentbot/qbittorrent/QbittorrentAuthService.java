package ru.xataaa.torrentbot.qbittorrent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.config.QbittorrentProperties;
import ru.xataaa.torrentbot.config.WebClientConfig;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.retry.RetryExecutor;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Slf4j
@Service
public class QbittorrentAuthService {

    private final QbittorrentProperties qbittorrentProperties;
    private final WebClient.Builder webClientBuilder;
    private final RetryExecutor retryExecutor;
    private final Map<DownloadTarget, String> sessionCookies = new ConcurrentHashMap<>();

    public QbittorrentAuthService(QbittorrentProperties qbittorrentProperties, WebClient.Builder webClientBuilder, RetryExecutor retryExecutor) {
        this.qbittorrentProperties = qbittorrentProperties;
        this.webClientBuilder = webClientBuilder;
        this.retryExecutor = retryExecutor;
    }

    public String getSessionCookie() {
        return getSessionCookie(DownloadTarget.VPS);
    }

    public String getSessionCookie(DownloadTarget downloadTarget) {
        String currentSessionCookie = sessionCookies.get(downloadTarget);
        if (currentSessionCookie != null) {
            return currentSessionCookie;
        }
        return login(downloadTarget);
    }

    public String login() {
        return login(DownloadTarget.VPS);
    }

    public String login(DownloadTarget downloadTarget) {
        return retryExecutor.execute("qbittorrent.login." + downloadTarget.name().toLowerCase(), () -> loginOnce(downloadTarget));
    }

    public void invalidateSession() {
        invalidateSession(DownloadTarget.VPS);
    }

    public void invalidateSession(DownloadTarget downloadTarget) {
        sessionCookies.remove(downloadTarget);
    }

    private String loginOnce(DownloadTarget downloadTarget) {
        QbittorrentProperties.TargetProperties targetProperties = qbittorrentProperties.target(downloadTarget);
        WebClient webClient = webClientBuilder
                .baseUrl(targetProperties.baseUrl())
                .clientConnector(WebClientConfig.connector(targetProperties.connectTimeoutMs(), targetProperties.requestTimeoutMs()))
                .build();
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", targetProperties.username());
        formData.add("password", targetProperties.password());

        String cookie = webClient.post()
                .uri("/api/v2/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode().is4xxClientError()) {
                        throw new NonRetryableOperationException(ErrorCode.QBITTORRENT_AUTH_FAILED, "qBittorrent auth rejected credentials");
                    }
                    if (clientResponse.statusCode().isError()) {
                        throw new RetryableOperationException(ErrorCode.QBITTORRENT_UNAVAILABLE, "qBittorrent auth unavailable");
                    }
                    List<String> cookies = clientResponse.headers().header(HttpHeaders.SET_COOKIE);
                    return clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> extractSidCookie(cookies, body));
                })
                .block(Duration.ofMillis(targetProperties.requestTimeoutMs()));

        sessionCookies.put(downloadTarget, cookie);
        return cookie;
    }

    private String extractSidCookie(List<String> cookies, String body) {
        if (body != null && !body.isBlank() && !"Ok.".equals(body)) {
            throw new NonRetryableOperationException(ErrorCode.QBITTORRENT_AUTH_FAILED, "qBittorrent login failed");
        }
        String setCookieHeader = cookies.stream()
                .filter(cookie -> cookie.startsWith("SID=") || cookie.startsWith("QBT_SID"))
                .findFirst()
                .orElseThrow(() -> new RetryableOperationException(ErrorCode.QBITTORRENT_AUTH_FAILED, "qBittorrent did not return SID cookie"));
        int semicolonIndex = setCookieHeader.indexOf(';');
        if (semicolonIndex < 0) {
            return setCookieHeader;
        }
        return setCookieHeader.substring(0, semicolonIndex);
    }
}
