package ru.xataaa.torrentbot.media;

public record S3UploadResult(
        String objectKey,
        String fileName,
        boolean alreadyExists
) {
}
