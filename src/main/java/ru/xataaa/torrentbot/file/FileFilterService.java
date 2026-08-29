package ru.xataaa.torrentbot.file;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FileFilterService {

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".webm");
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(".nfo", ".txt", ".jpg", ".png", ".url", ".html", ".exe", ".bat", ".cmd", ".sh");

    public DownloadFileStatus classify(String fileName, long sizeBytes) {
        if (sizeBytes <= 0) {
            return DownloadFileStatus.SKIPPED_UNSUPPORTED;
        }
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        if (VIDEO_EXTENSIONS.stream().anyMatch(lowerFileName::endsWith)) {
            return DownloadFileStatus.READY_TO_UPLOAD;
        }
        if (BLOCKED_EXTENSIONS.stream().anyMatch(lowerFileName::endsWith)) {
            return DownloadFileStatus.SKIPPED_UNSUPPORTED;
        }
        return DownloadFileStatus.SKIPPED_UNSUPPORTED;
    }
}
