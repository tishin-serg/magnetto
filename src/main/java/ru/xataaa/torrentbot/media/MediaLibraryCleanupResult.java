package ru.xataaa.torrentbot.media;

public record MediaLibraryCleanupResult(
        long deletedFiles,
        long deletedBytes
) {
}
