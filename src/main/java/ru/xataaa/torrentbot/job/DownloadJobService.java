package ru.xataaa.torrentbot.job;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.DiskSpaceService;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.common.MagnetValidator;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.preferences.DownloadPreferences;
import ru.xataaa.torrentbot.preferences.DownloadPreferencesRepository;
import ru.xataaa.torrentbot.speed.DownloadAlternative;
import ru.xataaa.torrentbot.speed.DownloadAlternativeRepository;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchResult;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadJobService {

    private final AppProperties appProperties;
    private final MagnetValidator magnetValidator;
    private final TimeProvider timeProvider;
    private final DownloadJobRepository downloadJobRepository;
    private final TelegramMessageService telegramMessageService;
    private final DownloadOrchestrator downloadOrchestrator;
    private final DiskSpaceService diskSpaceService;
    private final FileSizeFormatter fileSizeFormatter;
    private final DownloadPreferencesRepository preferencesRepository;
    private final DownloadAlternativeRepository alternativeRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private DownloadSizeGuard sizeGuard;

    public void startDownload(Long chatId, String magnetUrl) {
        startDownload(chatId, magnetUrl, 0L, DownloadTarget.VPS);
    }

    public void startDownload(Long chatId, String magnetUrl, long expectedSizeBytes) {
        startDownload(chatId, magnetUrl, expectedSizeBytes, DownloadTarget.VPS);
    }

    public void startDownload(Long chatId, String magnetUrl, long expectedSizeBytes, DownloadTarget downloadTarget) {
        startDownload(chatId, magnetUrl, expectedSizeBytes, downloadTarget, null);
    }

    public void startDownload(Long chatId, String magnetUrl, long expectedSizeBytes, DownloadTarget downloadTarget, String preferredTorrentName) {
        startDownload(chatId, magnetUrl, expectedSizeBytes, downloadTarget, preferredTorrentName, null);
    }

    public void startDownload(Long chatId, String magnetUrl, long expectedSizeBytes, DownloadTarget downloadTarget,
            String preferredTorrentName, DownloadPreferences preferences) {
        startDownload(chatId, magnetUrl, expectedSizeBytes, downloadTarget, preferredTorrentName, preferences, List.of());
    }

    public void startDownload(Long chatId, String magnetUrl, long expectedSizeBytes, DownloadTarget downloadTarget,
            String preferredTorrentName, DownloadPreferences preferences, List<TorrentSearchResult> alternatives) {
        boolean enforceSizePolicy = preferences != null;
        DownloadPreferences effectivePreferences = preferences == null ? preferencesRepository.find(chatId) : preferences;
        createAndStart(UUID.randomUUID(), chatId, magnetUrl, expectedSizeBytes, downloadTarget, preferredTorrentName,
                effectivePreferences, alternatives, List.of(), enforceSizePolicy);
    }

    public void startReplacement(UUID replacementJobId, DownloadJob source, DownloadAlternative selected,
                                 List<DownloadAlternative> remainingAlternatives) {
        DownloadPreferences snapshot = new DownloadPreferences(0, Long.MAX_VALUE, 1, source.getDownloadTarget(),
                source.getMinDownloadSpeedBytesPerSecond(), source.isAutoReplaceSlowDownload());
        createAndStart(replacementJobId, source.getChatId(), selected.magnetUrl(), selected.sizeBytes(),
                source.getDownloadTarget(), selected.title(), snapshot, List.of(), remainingAlternatives, false);
    }

    private void createAndStart(UUID jobId, Long chatId, String magnetUrl, long expectedSizeBytes,
            DownloadTarget downloadTarget, String preferredTorrentName, DownloadPreferences preferences,
            List<TorrentSearchResult> alternatives, List<DownloadAlternative> savedAlternatives,
            boolean enforceSizePolicy) {
        if (!appProperties.isChatAllowed(chatId)) {
            telegramMessageService.sendText(chatId, "Доступ запрещён.");
            log.warn("Access denied: chatId={}", chatId);
            return;
        }
        if (!magnetValidator.isValid(magnetUrl)) {
            telegramMessageService.sendText(chatId, "Некорректная magnet-ссылка. Она должна начинаться с magnet:?xt=urn:btih:");
            return;
        }

        DownloadTarget effectiveDownloadTarget = downloadTarget == null ? DownloadTarget.VPS : downloadTarget;
        LocalDateTime now = timeProvider.now();
        DownloadJob downloadJob = DownloadJob.builder()
                .id(jobId)
                .chatId(chatId)
                .magnetUrl(magnetUrl)
                .magnetUrlHash(sha256(magnetUrl))
                .torrentName(normalizePreferredTorrentName(preferredTorrentName))
                .status(DownloadJobStatus.CREATED)
                .downloadTarget(effectiveDownloadTarget)
                .targetStatus(TargetStatus.READY)
                .retryCount(0)
                .deleteAfterUpload(appProperties.deleteAfterSuccessfulUpload())
                .lastReportedProgressPercent(-1)
                .minDownloadSpeedBytesPerSecond(preferences.minDownloadSpeedBytesPerSecond())
                .autoReplaceSlowDownload(preferences.autoReplaceSlowDownload())
                .createdAt(now)
                .updatedAt(now)
                .build();

        if (enforceSizePolicy) sizeGuard.saveJob(downloadJob, preferences);
        else downloadJobRepository.save(downloadJob);
        alternativeRepository.saveResults(jobId, magnetUrl, alternatives);
        alternativeRepository.saveAlternatives(jobId, savedAlternatives);
        log.info("Creating download job: jobId={}, chatId={}, downloadTarget={}", jobId, chatId, effectiveDownloadTarget);
        String acceptedText = "Задача принята.\nКуда скачивать: " + targetLabel(effectiveDownloadTarget) + ".\nЯ начну загрузку и буду обновлять этот статус.";
        Long statusMessageId = telegramMessageService.sendText(chatId, acceptedText).getMessageId();
        if (statusMessageId != null) {
            downloadJobRepository.updateStatusMessageId(jobId, statusMessageId);
        }
        downloadOrchestrator.processJob(jobId);
    }

    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException("SHA-256 is unavailable", noSuchAlgorithmException);
        }
    }

    private String targetLabel(DownloadTarget downloadTarget) {
        return switch (downloadTarget) {
            case HOME_PC -> "домашний ПК";
            case S3, S3_LATER -> "S3";
            case VPS -> "VPS";
        };
    }

    private String normalizePreferredTorrentName(String preferredTorrentName) {
        if (preferredTorrentName == null || preferredTorrentName.isBlank()) {
            return null;
        }
        return preferredTorrentName.trim();
    }
}
