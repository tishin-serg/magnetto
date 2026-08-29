package ru.xataaa.torrentbot.qbittorrent;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.config.QbittorrentProperties;
import ru.xataaa.torrentbot.config.WebClientConfig;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentFile;
import ru.xataaa.torrentbot.qbittorrent.dto.QbittorrentTorrentInfo;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Slf4j
@Component
public class QbittorrentClient {

    private final QbittorrentProperties qbittorrentProperties;
    private final QbittorrentAuthService qbittorrentAuthService;
    private final WebClient.Builder webClientBuilder;

    public QbittorrentClient(QbittorrentProperties qbittorrentProperties, QbittorrentAuthService qbittorrentAuthService, WebClient.Builder webClientBuilder) {
        this.qbittorrentProperties = qbittorrentProperties;
        this.qbittorrentAuthService = qbittorrentAuthService;
        this.webClientBuilder = webClientBuilder;
    }

    public void login() {
        login(DownloadTarget.VPS);
    }

    public void login(DownloadTarget downloadTarget) {
        qbittorrentAuthService.login(downloadTarget);
    }

    public void addMagnet(String magnetUrl, String savePath, String tag) {
        addMagnet(DownloadTarget.VPS, magnetUrl, savePath, tag);
    }

    public void addMagnet(DownloadTarget downloadTarget, String magnetUrl, String savePath, String tag) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("urls", magnetUrl);
        formData.add("savepath", savePath);
        formData.add("tags", tag);
        postForm(downloadTarget, "/api/v2/torrents/add", formData);
    }

    public List<QbittorrentTorrentInfo> getTorrentsInfo() {
        return getTorrentsInfo(DownloadTarget.VPS);
    }

    public List<QbittorrentTorrentInfo> getTorrentsInfo(DownloadTarget downloadTarget) {
        QbittorrentProperties.TargetProperties targetProperties = qbittorrentProperties.target(downloadTarget);
        List<QbittorrentTorrentInfo> torrents = webClient(downloadTarget).get()
                .uri("/api/v2/torrents/info")
                .header(HttpHeaders.COOKIE, qbittorrentAuthService.getSessionCookie(downloadTarget))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<QbittorrentTorrentInfo>>() {
                })
                .onErrorMap(throwable -> mapQbittorrentError(downloadTarget, throwable))
                .block(Duration.ofMillis(targetProperties.requestTimeoutMs()));
        return torrents == null ? List.of() : torrents;
    }

    public List<QbittorrentTorrentFile> getTorrentFiles(String hash) {
        return getTorrentFiles(DownloadTarget.VPS, hash);
    }

    public List<QbittorrentTorrentFile> getTorrentFiles(DownloadTarget downloadTarget, String hash) {
        QbittorrentProperties.TargetProperties targetProperties = qbittorrentProperties.target(downloadTarget);
        List<QbittorrentTorrentFile> files = webClient(downloadTarget).get()
                .uri(uriBuilder -> uriBuilder.path("/api/v2/torrents/files").queryParam("hash", hash).build())
                .header(HttpHeaders.COOKIE, qbittorrentAuthService.getSessionCookie(downloadTarget))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<QbittorrentTorrentFile>>() {
                })
                .onErrorMap(throwable -> mapQbittorrentError(downloadTarget, throwable))
                .block(Duration.ofMillis(targetProperties.requestTimeoutMs()));
        return files == null ? List.of() : files;
    }

    public void deleteTorrent(String hash, boolean deleteFiles) {
        deleteTorrent(DownloadTarget.VPS, hash, deleteFiles);
    }

    public void deleteTorrent(DownloadTarget downloadTarget, String hash, boolean deleteFiles) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("hashes", hash);
        formData.add("deleteFiles", Boolean.toString(deleteFiles));
        postForm(downloadTarget, "/api/v2/torrents/delete", formData);
    }

    public void setFilePriority(String hash, List<Integer> fileIndexes, int priority) {
        setFilePriority(DownloadTarget.VPS, hash, fileIndexes, priority);
    }

    public void setFilePriority(DownloadTarget downloadTarget, String hash, List<Integer> fileIndexes, int priority) {
        if (fileIndexes.isEmpty()) {
            return;
        }
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("hash", hash);
        formData.add("id", joinFileIndexes(fileIndexes));
        formData.add("priority", Integer.toString(priority));
        postForm(downloadTarget, "/api/v2/torrents/filePrio", formData);
    }

    public void pauseTorrent(String hash) {
        pauseTorrent(DownloadTarget.VPS, hash);
    }

    public void pauseTorrent(DownloadTarget downloadTarget, String hash) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("hashes", hash);
        postForm(downloadTarget, "/api/v2/torrents/pause", formData);
    }

    public void resumeTorrent(String hash) {
        resumeTorrent(DownloadTarget.VPS, hash);
    }

    public void resumeTorrent(DownloadTarget downloadTarget, String hash) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("hashes", hash);
        postForm(downloadTarget, "/api/v2/torrents/resume", formData);
    }

    private void postForm(DownloadTarget downloadTarget, String path, MultiValueMap<String, String> formData) {
        QbittorrentProperties.TargetProperties targetProperties = qbittorrentProperties.target(downloadTarget);
        webClient(downloadTarget).post()
                .uri(path)
                .header(HttpHeaders.COOKIE, qbittorrentAuthService.getSessionCookie(downloadTarget))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .toBodilessEntity()
                .onErrorMap(throwable -> mapQbittorrentError(downloadTarget, throwable))
                .block(Duration.ofMillis(targetProperties.requestTimeoutMs()));
    }

    private WebClient webClient(DownloadTarget downloadTarget) {
        QbittorrentProperties.TargetProperties targetProperties = qbittorrentProperties.target(downloadTarget);
        return webClientBuilder
                .baseUrl(targetProperties.baseUrl())
                .clientConnector(WebClientConfig.connector(targetProperties.connectTimeoutMs(), targetProperties.requestTimeoutMs()))
                .build();
    }

    private String joinFileIndexes(List<Integer> fileIndexes) {
        StringBuilder builder = new StringBuilder();
        for (Integer fileIndex : fileIndexes) {
            if (fileIndex == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("|");
            }
            builder.append(fileIndex);
        }
        return builder.toString();
    }

    private Throwable mapQbittorrentError(DownloadTarget downloadTarget, Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientResponseException
                && (webClientResponseException.getStatusCode().value() == 401
                || webClientResponseException.getStatusCode().value() == 403)) {
            qbittorrentAuthService.invalidateSession(downloadTarget);
            return new RetryableOperationException(ErrorCode.QBITTORRENT_AUTH_FAILED, "qBittorrent session expired or was rejected", throwable);
        }
        return throwable;
    }
}
