package ru.xataaa.torrentbot.downloadlink;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.config.DownloadsProperties;
import ru.xataaa.torrentbot.config.TelegramProperties;
import ru.xataaa.torrentbot.media.HomeMediaLibraryFile;
import ru.xataaa.torrentbot.media.HomeWebdavMediaLibraryService;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeDownloadLinkService {

    private static final int TOKEN_BYTES = 32;

    private final HomeDownloadLinkRepository homeDownloadLinkRepository;
    private final HomeWebdavMediaLibraryService homeWebdavMediaLibraryService;
    private final DownloadsProperties downloadsProperties;
    private final TelegramProperties telegramProperties;
    private final TimeProvider timeProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public HomeDownloadLink createLink(Long chatId, String fileKey) {
        HomeMediaLibraryFile file = findFileByKey(fileKey)
                .orElseThrow(() -> new NonRetryableOperationException(ErrorCode.FILE_NOT_FOUND, "Home media file not found"));
        LocalDateTime now = timeProvider.now();
        HomeDownloadLink homeDownloadLink = HomeDownloadLink.builder()
                .id(UUID.randomUUID())
                .token(generateToken())
                .chatId(chatId)
                .fileName(file.relativePath())
                .fileSizeBytes(file.sizeBytes())
                .status(DownloadLinkStatus.ACTIVE)
                .expiresAt(now.plusHours(telegramProperties.file().downloadLinkTtlHours()))
                .createdAt(now)
                .build();
        homeDownloadLinkRepository.save(homeDownloadLink);
        log.info("Created temporary home download link: fileName={}, sizeBytes={}, expiresAt={}, tokenPreview={}",
                file.relativePath(), file.sizeBytes(), homeDownloadLink.getExpiresAt(), tokenPreview(homeDownloadLink.getToken()));
        return homeDownloadLink;
    }

    public Optional<HomeDownloadLink> getValidLink(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<HomeDownloadLink> link = homeDownloadLinkRepository.findByToken(token);
        if (link.isEmpty()) {
            return Optional.empty();
        }
        HomeDownloadLink homeDownloadLink = link.get();
        if (homeDownloadLink.getStatus() != DownloadLinkStatus.ACTIVE) {
            return Optional.empty();
        }
        if (!homeDownloadLink.getExpiresAt().isAfter(timeProvider.now())) {
            return Optional.empty();
        }
        if (findFileByName(homeDownloadLink.getFileName()).isEmpty()) {
            return Optional.empty();
        }
        homeDownloadLinkRepository.markUsed(homeDownloadLink.getId(), timeProvider.now());
        return Optional.of(homeDownloadLink);
    }

    public int expireOldLinks() {
        LocalDateTime now = timeProvider.now();
        int expiredCount = 0;
        for (HomeDownloadLink homeDownloadLink : homeDownloadLinkRepository.findExpiredActiveLinks(now)) {
            homeDownloadLinkRepository.updateStatus(homeDownloadLink.getId(), DownloadLinkStatus.EXPIRED);
            expiredCount++;
        }
        return expiredCount;
    }

    public String publicUrl(HomeDownloadLink homeDownloadLink) {
        return trimTrailingSlash(downloadsProperties.publicBaseUrl()) + "/home-download/" + homeDownloadLink.getToken();
    }

    public long ttlHours() {
        return telegramProperties.file().downloadLinkTtlHours();
    }

    public String fileKey(HomeMediaLibraryFile file) {
        return fileKey(file.relativePath());
    }

    public String fileKey(String fileName) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(fileName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return encoded.substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public Optional<HomeMediaLibraryFile> findFileByName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("\\")) {
            return Optional.empty();
        }
        List<HomeMediaLibraryFile> files = homeWebdavMediaLibraryService.listFiles();
        for (HomeMediaLibraryFile file : files) {
            if (file.relativePath().equals(fileName)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    public Optional<HomeMediaLibraryFile> findFileByKey(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return Optional.empty();
        }
        List<HomeMediaLibraryFile> files = homeWebdavMediaLibraryService.listFiles();
        for (HomeMediaLibraryFile file : files) {
            if (fileKey(file).equals(fileKey)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String tokenPreview(String token) {
        if (token == null || token.length() < 12) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 6);
    }
}
