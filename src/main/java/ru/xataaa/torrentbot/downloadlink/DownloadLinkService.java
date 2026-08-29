package ru.xataaa.torrentbot.downloadlink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.config.DownloadsProperties;
import ru.xataaa.torrentbot.config.TelegramProperties;
import ru.xataaa.torrentbot.file.DownloadFile;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadLinkService {

    private static final int TOKEN_BYTES = 32;
    private static final String PUBLIC_LINKS_DIRECTORY_NAME = "public-links";

    private final DownloadLinkRepository downloadLinkRepository;
    private final DownloadsProperties downloadsProperties;
    private final TelegramProperties telegramProperties;
    private final TimeProvider timeProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public DownloadLink createDownloadLink(Long chatId, DownloadFile downloadFile) {
        Optional<DownloadLink> existingLink = downloadLinkRepository.findActiveByFileId(downloadFile.getId());
        if (existingLink.isPresent()) {
            return existingLink.get();
        }

        Path sourcePath = resolveInsideStorage(downloadFile.getRelativePath());
        if (!Files.isRegularFile(sourcePath)) {
            throw new NonRetryableOperationException(ErrorCode.FILE_NOT_FOUND, "File not found: " + downloadFile.getFileName());
        }

        String token = generateToken();
        String storedFileName = token + extensionOf(downloadFile.getFileName());
        Path publicLinksDirectory = publicLinksDirectory();
        Path linkPath = publicLinksDirectory.resolve(storedFileName).normalize();
        if (!linkPath.startsWith(publicLinksDirectory)) {
            throw new NonRetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Invalid stored file path");
        }

        try {
            Files.createDirectories(publicLinksDirectory);
            Files.createLink(linkPath, sourcePath);
        } catch (IOException ioException) {
            throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Failed to create download link file", ioException);
        }

        LocalDateTime now = timeProvider.now();
        DownloadLink downloadLink = DownloadLink.builder()
                .id(UUID.randomUUID())
                .jobId(downloadFile.getJobId())
                .fileId(downloadFile.getId())
                .token(token)
                .chatId(chatId)
                .originalFileName(safeOriginalFileName(downloadFile.getFileName()))
                .storedFileName(storedFileName)
                .filePath(linkPath.toString())
                .fileSizeBytes(downloadFile.getSizeBytes())
                .status(DownloadLinkStatus.ACTIVE)
                .expiresAt(now.plusHours(telegramProperties.file().downloadLinkTtlHours()))
                .createdAt(now)
                .build();
        downloadLinkRepository.save(downloadLink);
        log.info("Created temporary download link: jobId={}, fileName={}, sizeBytes={}, expiresAt={}, tokenPreview={}",
                downloadFile.getJobId(), downloadFile.getFileName(), downloadFile.getSizeBytes(), downloadLink.getExpiresAt(), tokenPreview(token));
        return downloadLink;
    }

    public Optional<DownloadLink> getValidDownloadLink(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<DownloadLink> link = downloadLinkRepository.findByToken(token);
        if (link.isEmpty()) {
            return Optional.empty();
        }
        DownloadLink downloadLink = link.get();
        if (downloadLink.getStatus() != DownloadLinkStatus.ACTIVE) {
            return Optional.empty();
        }
        if (!downloadLink.getExpiresAt().isAfter(timeProvider.now())) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(Path.of(downloadLink.getFilePath()))) {
            return Optional.empty();
        }
        downloadLinkRepository.markUsed(downloadLink.getId(), timeProvider.now());
        return Optional.of(downloadLink);
    }

    public boolean hasActiveLinks(UUID jobId) {
        return downloadLinkRepository.existsActiveByJobId(jobId);
    }

    public int expireOldLinks() {
        LocalDateTime now = timeProvider.now();
        int expiredCount = 0;
        for (DownloadLink downloadLink : downloadLinkRepository.findExpiredActiveLinks(now)) {
            downloadLinkRepository.updateStatus(downloadLink.getId(), DownloadLinkStatus.EXPIRED, now);
            expiredCount++;
        }
        return expiredCount;
    }

    public int deleteExpiredFiles() {
        LocalDateTime now = timeProvider.now();
        int deletedCount = 0;
        for (DownloadLink downloadLink : downloadLinkRepository.findByStatus(DownloadLinkStatus.EXPIRED)) {
            Path filePath = Path.of(downloadLink.getFilePath()).normalize();
            if (!filePath.startsWith(publicLinksDirectory())) {
                log.warn("Skipping suspicious expired link file path: linkId={}, tokenPreview={}",
                        downloadLink.getId(), tokenPreview(downloadLink.getToken()));
                continue;
            }
            try {
                Files.deleteIfExists(filePath);
                downloadLinkRepository.updateStatus(downloadLink.getId(), DownloadLinkStatus.DELETED, now);
                deletedCount++;
            } catch (IOException ioException) {
                log.warn("Failed to delete expired download link file: linkId={}, tokenPreview={}, error={}",
                        downloadLink.getId(), tokenPreview(downloadLink.getToken()), ioException.getMessage());
            }
        }
        return deletedCount;
    }

    public String publicUrl(DownloadLink downloadLink) {
        return trimTrailingSlash(downloadsProperties.publicBaseUrl()) + "/download/" + downloadLink.getToken();
    }

    public String xAccelRedirectPath(DownloadLink downloadLink) {
        return ensureTrailingSlash(downloadsProperties.internalNginxLocationPrefix()) + downloadLink.getStoredFileName();
    }

    public long ttlHours() {
        return telegramProperties.file().downloadLinkTtlHours();
    }

    private Path resolveInsideStorage(String relativePath) {
        Path storagePath = Path.of(downloadsProperties.storagePath()).toAbsolutePath().normalize();
        Path resolvedPath = storagePath.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(storagePath)) {
            throw new NonRetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Invalid file path");
        }
        return resolvedPath;
    }

    private Path publicLinksDirectory() {
        return Path.of(downloadsProperties.storagePath()).toAbsolutePath().normalize().resolve(PUBLIC_LINKS_DIRECTORY_NAME).normalize();
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String extensionOf(String fileName) {
        String safeFileName = safeOriginalFileName(fileName).toLowerCase(Locale.ROOT);
        int dotIndex = safeFileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == safeFileName.length() - 1) {
            return ".bin";
        }
        String extension = safeFileName.substring(dotIndex);
        if (!extension.matches("\\.[a-z0-9]{1,10}")) {
            return ".bin";
        }
        return extension;
    }

    private String safeOriginalFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "download.bin";
        }
        return fileName.replace("\\", "_").replace("/", "_").replace("\r", "_").replace("\n", "_");
    }

    private String tokenPreview(String token) {
        if (token == null || token.length() < 12) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 6);
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String ensureTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value;
        }
        return value + "/";
    }
}
