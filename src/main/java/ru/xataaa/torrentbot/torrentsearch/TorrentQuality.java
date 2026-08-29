package ru.xataaa.torrentbot.torrentsearch;

import java.util.List;
import java.util.Locale;

public enum TorrentQuality {
    ANY("any", "Любое", List.of()),
    UHD_2160P("2160p", "2160p", List.of("2160p", "4k", "uhd")),
    FULL_HD_1080P("1080p", "1080p", List.of("1080p")),
    HD_720P("720p", "720p", List.of("720p")),
    WEB_DL("webdl", "WEB-DL", List.of("web-dl", "webdl")),
    BLURAY("bluray", "BluRay/BDRip", List.of("bluray", "blu-ray", "bdrip", "bdremux", "remux"));

    private final String code;
    private final String displayName;
    private final List<String> markers;

    TorrentQuality(String code, String displayName, List<String> markers) {
        this.code = code;
        this.displayName = displayName;
        this.markers = markers;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public boolean matches(String title) {
        if (this == ANY) {
            return true;
        }
        if (title == null || title.isBlank()) {
            return false;
        }
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        for (String marker : markers) {
            if (normalizedTitle.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    public static TorrentQuality fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ANY;
        }
        for (TorrentQuality quality : values()) {
            if (quality.code.equalsIgnoreCase(code)) {
                return quality;
            }
        }
        return ANY;
    }
}
