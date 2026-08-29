package ru.xataaa.torrentbot.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.config.MediaLibraryProperties;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaLibraryService {

    private final MediaLibraryProperties mediaLibraryProperties;

    public MediaLibraryResult addToLibrary(Path sourcePath, String originalFileName) {
        if (!mediaLibraryProperties.enabled()) {
            throw new NonRetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Media library is disabled");
        }
        Path normalizedSourcePath = sourcePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedSourcePath)) {
            throw new NonRetryableOperationException(ErrorCode.FILE_NOT_FOUND, "File not found: " + originalFileName);
        }

        Path libraryDirectory = Path.of(mediaLibraryProperties.path()).toAbsolutePath().normalize();
        String safeFileName = safeFileName(originalFileName);
        Path targetPath = uniqueTargetPath(libraryDirectory, safeFileName);
        if (!targetPath.startsWith(libraryDirectory)) {
            throw new NonRetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Invalid media library target path");
        }

        try {
            Files.createDirectories(libraryDirectory);
            if (Files.exists(targetPath) && Files.size(targetPath) == Files.size(normalizedSourcePath)) {
                return new MediaLibraryResult(targetPath, targetPath.getFileName().toString(), mediaLibraryProperties.moveStrategy(), true);
            }
            MediaLibraryProperties.MoveStrategy usedStrategy = copyByConfiguredStrategy(normalizedSourcePath, targetPath);
            log.info("Added file to media library: sourceFile={}, targetFile={}, strategy={}",
                    normalizedSourcePath.getFileName(), targetPath.getFileName(), usedStrategy);
            return new MediaLibraryResult(targetPath, targetPath.getFileName().toString(), usedStrategy, false);
        } catch (IOException ioException) {
            throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Failed to add file to media library", ioException);
        }
    }

    public String publicWebdavUrl() {
        return mediaLibraryProperties.publicWebdavUrl();
    }

    public List<MediaLibraryFile> listFiles() {
        Path libraryDirectory = Path.of(mediaLibraryProperties.path()).toAbsolutePath().normalize();
        if (!Files.exists(libraryDirectory)) {
            return List.of();
        }
        if (!Files.isDirectory(libraryDirectory)) {
            throw new NonRetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Media library path is not a directory");
        }

        List<MediaLibraryFile> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(libraryDirectory, 1)) {
            for (Path path : paths.toList()) {
                Path normalizedPath = path.toAbsolutePath().normalize();
                if (normalizedPath.equals(libraryDirectory) || !normalizedPath.startsWith(libraryDirectory) || !Files.isRegularFile(normalizedPath)) {
                    continue;
                }
                LocalDateTime modifiedAt = LocalDateTime.ofInstant(
                        Files.getLastModifiedTime(normalizedPath).toInstant(),
                        TimeProvider.MOSCOW_ZONE_ID
                );
                files.add(new MediaLibraryFile(
                        normalizedPath.getFileName().toString(),
                        Files.size(normalizedPath),
                        modifiedAt
                ));
            }
            files.sort(Comparator.comparing(MediaLibraryFile::modifiedAt).reversed());
            return files;
        } catch (IOException ioException) {
            throw new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Failed to list media library", ioException);
        }
    }

    public MediaLibraryCleanupResult cleanupAllFiles() {
        Path libraryDirectory = Path.of(mediaLibraryProperties.path()).toAbsolutePath().normalize();
        if (!Files.exists(libraryDirectory)) {
            return new MediaLibraryCleanupResult(0, 0L);
        }
        if (!Files.isDirectory(libraryDirectory)) {
            throw new NonRetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Media library path is not a directory");
        }

        long deletedFiles = 0L;
        long deletedBytes = 0L;
        try (Stream<Path> paths = Files.walk(libraryDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Path normalizedPath = path.toAbsolutePath().normalize();
                if (normalizedPath.equals(libraryDirectory) || !normalizedPath.startsWith(libraryDirectory)) {
                    continue;
                }
                if (Files.isRegularFile(normalizedPath)) {
                    long sizeBytes = Files.size(normalizedPath);
                    Files.deleteIfExists(normalizedPath);
                    deletedFiles++;
                    deletedBytes += sizeBytes;
                    continue;
                }
                if (Files.isDirectory(normalizedPath)) {
                    Files.deleteIfExists(normalizedPath);
                }
            }
            log.info("Media library cleanup completed: path={}, deletedFiles={}, deletedBytes={}",
                    libraryDirectory, deletedFiles, deletedBytes);
            return new MediaLibraryCleanupResult(deletedFiles, deletedBytes);
        } catch (IOException ioException) {
            throw new RetryableOperationException(ErrorCode.CLEANUP_FAILED, "Failed to cleanup media library", ioException);
        }
    }

    private MediaLibraryProperties.MoveStrategy copyByConfiguredStrategy(Path sourcePath, Path targetPath) throws IOException {
        try {
            copyByStrategy(sourcePath, targetPath, mediaLibraryProperties.moveStrategy());
            return mediaLibraryProperties.moveStrategy();
        } catch (IOException firstException) {
            if (mediaLibraryProperties.fallbackMoveStrategy() == mediaLibraryProperties.moveStrategy()) {
                throw firstException;
            }
            copyByStrategy(sourcePath, targetPath, mediaLibraryProperties.fallbackMoveStrategy());
            return mediaLibraryProperties.fallbackMoveStrategy();
        }
    }

    private void copyByStrategy(Path sourcePath, Path targetPath, MediaLibraryProperties.MoveStrategy moveStrategy) throws IOException {
        switch (moveStrategy) {
            case HARDLINK -> Files.createLink(targetPath, sourcePath);
            case COPY -> Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
            case MOVE -> Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private Path uniqueTargetPath(Path libraryDirectory, String safeFileName) {
        Path targetPath = libraryDirectory.resolve(safeFileName).normalize();
        if (!Files.exists(targetPath)) {
            return targetPath;
        }
        String extension = extensionOf(safeFileName);
        String baseName = extension.isBlank() ? safeFileName : safeFileName.substring(0, safeFileName.length() - extension.length());
        String suffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        return libraryDirectory.resolve(baseName + suffix + extension).normalize();
    }

    private String safeFileName(String originalFileName) {
        String fileName = originalFileName == null || originalFileName.isBlank() ? "movie.bin" : originalFileName;
        String normalized = fileName
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\r", "_")
                .replace("\n", "_")
                .replace("..", "_")
                .trim();
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}._()\\- ]", "_");
        if (normalized.isBlank() || ".".equals(normalized)) {
            return "movie.bin";
        }
        return normalized;
    }

    private String extensionOf(String fileName) {
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        int dotIndex = lowerFileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return fileName.substring(dotIndex);
    }
}
