package ru.xataaa.torrentbot.job;

public enum DownloadTarget {
    VPS,
    HOME_PC,
    S3_LATER;

    public static DownloadTarget fromValue(String value) {
        if (value == null || value.isBlank()) {
            return VPS;
        }
        if ("S3".equalsIgnoreCase(value.trim())) {
            return S3_LATER;
        }
        return DownloadTarget.valueOf(value.trim());
    }
}
