package ru.xataaa.torrentbot.torrentsearch;

import java.util.List;
import java.util.Locale;

public enum VoiceFilter {
    ANY("any", "Любая", List.of()),
    DUBBED("dubbed", "Дубляж", List.of("дубляж", "дублирован", "dub")),
    PROFESSIONAL("professional", "Профессиональная", List.of("профессион", "проф.", "многоголос")),
    LOSTFILM("lostfilm", "LostFilm", List.of("lostfilm", "lost film")),
    HDREZKA("hdrezka", "HDRezka", List.of("hdrezka", "резка")),
    NEWSTUDIO("newstudio", "NewStudio", List.of("newstudio", "new studio")),
    ORIGINAL("original", "Оригинал", List.of("original", "оригинал", "eng", "en "));

    private final String code;
    private final String displayName;
    private final List<String> markers;

    VoiceFilter(String code, String displayName, List<String> markers) {
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

    public static VoiceFilter fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ANY;
        }
        for (VoiceFilter voiceFilter : values()) {
            if (voiceFilter.code.equalsIgnoreCase(code)) {
                return voiceFilter;
            }
        }
        return ANY;
    }
}
