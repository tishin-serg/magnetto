package ru.xataaa.torrentbot.application;

public record ShortcutPreferences(long minBytes, long maxBytes, int minSeeders,
                                  String quality, String voice, long minSpeedBytesPerSecond,
                                  boolean autoReplaceSlowDownload) {
    public ShortcutPreferences {
        if (minBytes < 0 || maxBytes <= 0 || minBytes > maxBytes) throw new IllegalArgumentException("Invalid size limits");
        if (minSeeders < 1) throw new IllegalArgumentException("Minimum seeders must be positive");
        if (minSpeedBytesPerSecond < 0) throw new IllegalArgumentException("Minimum speed cannot be negative");
        quality = quality == null || quality.isBlank() ? "any" : quality;
        voice = voice == null || voice.isBlank() ? "any" : voice;
    }
    public static ShortcutPreferences defaults() { return new ShortcutPreferences(0, Long.MAX_VALUE, 1, "any", "any", 0, false); }
}
