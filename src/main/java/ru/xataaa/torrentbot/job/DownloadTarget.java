package ru.xataaa.torrentbot.job;

public enum DownloadTarget {
    VPS,
    HOME_PC,
    S3,
    S3_LATER;

    public static DownloadTarget fromValue(String value) {
        if (value == null || value.isBlank()) {
            return VPS;
        }
        String normalized = value.trim();
        if ("S3_LATER".equalsIgnoreCase(normalized)) {
            return S3_LATER;
        }
        if ("S3".equalsIgnoreCase(normalized)) {
            return S3;
        }
        return DownloadTarget.valueOf(normalized);
    }

    public boolean isS3() {
        return this == S3 || this == S3_LATER;
    }
}
