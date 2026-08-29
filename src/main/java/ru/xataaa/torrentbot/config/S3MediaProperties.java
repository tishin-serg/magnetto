package ru.xataaa.torrentbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.s3")
public record S3MediaProperties(
        boolean enabled,
        String endpointUrl,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String prefix,
        long presignedLinkTtlHours,
        boolean pathStyleAccessEnabled,
        boolean deleteLocalAfterUpload
) {
    public S3MediaProperties {
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        if (prefix == null || prefix.isBlank()) {
            prefix = "media-library/";
        }
        prefix = normalizePrefix(prefix);
        if (presignedLinkTtlHours <= 0) {
            presignedLinkTtlHours = 24;
        }
    }

    private static String normalizePrefix(String value) {
        String normalized = value.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }
}
