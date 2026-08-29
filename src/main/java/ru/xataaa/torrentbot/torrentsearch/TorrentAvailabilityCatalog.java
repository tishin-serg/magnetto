package ru.xataaa.torrentbot.torrentsearch;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import ru.xataaa.torrentbot.movie.MovieMetadata;

public record TorrentAvailabilityCatalog(
        MovieMetadata movieMetadata,
        List<TorrentAvailabilityItem> items
) {
    public static final String ANY_VOICE = "Любая озвучка";
    public static final String ANY_QUALITY = "Любое качество";
    public static final String QUALITY_OPTIMAL = "Оптимально";
    public static final String QUALITY_SMALL = "Меньше размер";
    public static final String QUALITY_MAX = "Максимальное качество";

    private static final String VOICE_VIDEOFILM = "Videofilm";
    private static final String VOICE_LOSTFILM = "LostFilm";
    private static final String VOICE_UKR_ENG = "Ukr/Eng";
    private static final String VOICE_DUB = "\u0414\u0443\u0431\u043b\u044f\u0436";
    private static final List<String> VOICE_PRIORITY = List.of(
            VOICE_VIDEOFILM,
            VOICE_DUB,
            VOICE_LOSTFILM,
            VOICE_UKR_ENG,
            TorrentAvailabilityItem.UNKNOWN_VOICE
    );

    public TorrentAvailabilityCatalog {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean empty() {
        return items.isEmpty();
    }

    public List<SeasonOption> seasonOptions() {
        Map<Integer, List<TorrentAvailabilityItem>> bySeason = new TreeMap<>();
        for (TorrentAvailabilityItem item : items) {
            for (Integer seasonNumber : item.seasonNumbers()) {
                bySeason.computeIfAbsent(seasonNumber, ignored -> new java.util.ArrayList<>()).add(item);
            }
        }
        return bySeason.entrySet().stream()
                .map(entry -> new SeasonOption(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToInt(TorrentAvailabilityItem::seeders).max().orElse(0)
                ))
                .toList();
    }

    public List<ScopeOption> scopeOptions(Integer seasonNumber) {
        if (seasonNumber == null) {
            return List.of();
        }
        return List.of(
                        scopeOption("season-pack", "Сезон " + seasonNumber + " целиком", seasonNumber, ReleaseType.SEASON_PACK),
                        scopeOption("episodes", "Отдельные серии или файлы сезона", seasonNumber, ReleaseType.EPISODE, ReleaseType.EPISODE_RANGE, ReleaseType.SEASON_PACK),
                        scopeOption("multi-season", "Паки нескольких сезонов", seasonNumber, ReleaseType.MULTI_SEASON_PACK)
                ).stream()
                .filter(option -> option.resultCount() > 0)
                .toList();
    }

    public List<VoiceOption> voiceOptions(Integer seasonNumber) {
        return voiceOptions(seasonNumber, null);
    }

    public List<VoiceOption> voiceOptions(Integer seasonNumber, String scope) {
        List<TorrentAvailabilityItem> scopedItems = filtered(seasonNumber, scope, null, null);
        List<VoiceOption> options = scopedItems.stream()
                .flatMap(item -> voiceFilterTokens(item.voice()).stream()
                        .map(voice -> new VoiceItem(voice, item)))
                .collect(Collectors.groupingBy(VoiceItem::voice))
                .entrySet().stream()
                .map(entry -> new VoiceOption(
                        entry.getKey(),
                        distinctItems(entry.getValue()).size(),
                        distinctItems(entry.getValue()).stream().mapToInt(TorrentAvailabilityItem::seeders).max().orElse(0)
                ))
                .sorted(Comparator.comparingInt((VoiceOption option) -> voicePriority(option.voice()))
                        .thenComparing(Comparator.comparingInt((VoiceOption option) -> option.maxSeeders()).reversed())
                        .thenComparing(VoiceOption::voice, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (scopedItems.isEmpty()) {
            return options;
        }
        java.util.ArrayList<VoiceOption> withAny = new java.util.ArrayList<>();
        withAny.add(new VoiceOption(
                ANY_VOICE,
                scopedItems.size(),
                scopedItems.stream().mapToInt(TorrentAvailabilityItem::seeders).max().orElse(0)
        ));
        withAny.addAll(options);
        return List.copyOf(withAny);
    }

    public List<QualityOption> qualityOptions(Integer seasonNumber, String voice) {
        return qualityOptions(seasonNumber, null, voice);
    }

    public List<QualityOption> qualityOptions(Integer seasonNumber, String scope, String voice) {
        List<TorrentAvailabilityItem> scopedItems = filtered(seasonNumber, scope, voice, null);
        java.util.ArrayList<QualityOption> options = new java.util.ArrayList<>();
        if (!scopedItems.isEmpty()) {
            options.add(new QualityOption(
                    ANY_QUALITY,
                    scopedItems.size(),
                    scopedItems.stream().mapToInt(TorrentAvailabilityItem::seeders).max().orElse(0)
            ));
            addQualityGroup(options, scopedItems, QUALITY_OPTIMAL);
            addQualityGroup(options, scopedItems, QUALITY_SMALL);
            addQualityGroup(options, scopedItems, QUALITY_MAX);
        }
        List<QualityOption> technicalOptions = scopedItems.stream()
                .collect(Collectors.groupingBy(TorrentAvailabilityItem::quality))
                .entrySet().stream()
                .map(entry -> new QualityOption(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToInt(TorrentAvailabilityItem::seeders).max().orElse(0)
                ))
                .sorted(Comparator.comparingInt(QualityOption::maxSeeders).reversed()
                        .thenComparing(QualityOption::quality, String.CASE_INSENSITIVE_ORDER))
                .toList();
        options.addAll(technicalOptions);
        return List.copyOf(options);
    }

    public List<TorrentAvailabilityItem> filtered(Integer seasonNumber, String voice, String quality) {
        return filtered(seasonNumber, null, voice, quality);
    }

    public List<TorrentAvailabilityItem> filtered(Integer seasonNumber, String scope, String voice, String quality) {
        return items.stream()
                .filter(item -> seasonNumber == null || item.containsSeason(seasonNumber))
                .filter(item -> matchesScope(item, scope))
                .filter(item -> matchesVoice(item, voice))
                .filter(item -> matchesQuality(item, quality))
                .sorted(itemComparator(seasonNumber))
                .toList();
    }

    public Optional<String> firstVoice(Integer seasonNumber) {
        return firstVoice(seasonNumber, null);
    }

    public Optional<String> firstVoice(Integer seasonNumber, String scope) {
        return voiceOptions(seasonNumber, scope).stream().findFirst().map(VoiceOption::voice);
    }

    public Optional<String> firstQuality(Integer seasonNumber, String voice) {
        return firstQuality(seasonNumber, null, voice);
    }

    public Optional<String> firstQuality(Integer seasonNumber, String scope, String voice) {
        return qualityOptions(seasonNumber, scope, voice).stream().findFirst().map(QualityOption::quality);
    }

    public boolean hasVoice(String voice, Integer seasonNumber) {
        return hasVoice(voice, seasonNumber, null);
    }

    public boolean hasVoice(String voice, Integer seasonNumber, String scope) {
        if (voice == null || voice.isBlank()) {
            return false;
        }
        if (ANY_VOICE.equalsIgnoreCase(voice)) {
            return true;
        }
        return voiceOptions(seasonNumber, scope).stream()
                .anyMatch(option -> normalizedVoiceToken(option.voice()).equals(normalizedVoiceToken(voice)));
    }

    public boolean hasQuality(String quality, Integer seasonNumber, String voice) {
        return hasQuality(quality, seasonNumber, null, voice);
    }

    public boolean hasQuality(String quality, Integer seasonNumber, String scope, String voice) {
        if (quality == null || quality.isBlank()) {
            return false;
        }
        if (ANY_QUALITY.equalsIgnoreCase(quality)
                || QUALITY_OPTIMAL.equalsIgnoreCase(quality)
                || QUALITY_SMALL.equalsIgnoreCase(quality)
                || QUALITY_MAX.equalsIgnoreCase(quality)) {
            return true;
        }
        String normalizedQuality = quality.toLowerCase(Locale.ROOT);
        return qualityOptions(seasonNumber, scope, voice).stream()
                .anyMatch(option -> option.quality().toLowerCase(Locale.ROOT).equals(normalizedQuality));
    }

    public int multiSeasonPackCount() {
        return (int) items.stream()
                .filter(TorrentAvailabilityItem::multiSeasonPack)
                .count();
    }

    private ScopeOption scopeOption(String code, String label, Integer seasonNumber, ReleaseType... releaseTypes) {
        List<TorrentAvailabilityItem> scopedItems = items.stream()
                .filter(item -> item.containsSeason(seasonNumber))
                .filter(item -> {
                    for (ReleaseType releaseType : releaseTypes) {
                        if (item.releaseType() == releaseType) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
        return new ScopeOption(
                code,
                label,
                scopedItems.size(),
                scopedItems.stream().mapToInt(TorrentAvailabilityItem::seeders).max().orElse(0)
        );
    }

    private boolean matchesScope(TorrentAvailabilityItem item, String scope) {
        if (scope == null || scope.isBlank()) {
            return true;
        }
        return switch (scope) {
            case "season-pack" -> item.releaseType() == ReleaseType.SEASON_PACK;
            case "episodes" -> item.releaseType() == ReleaseType.EPISODE
                    || item.releaseType() == ReleaseType.EPISODE_RANGE
                    || item.releaseType() == ReleaseType.SEASON_PACK;
            case "multi-season" -> item.releaseType() == ReleaseType.MULTI_SEASON_PACK;
            default -> true;
        };
    }

    private boolean matchesVoice(TorrentAvailabilityItem item, String voice) {
        return voice == null || voice.isBlank()
                || ANY_VOICE.equalsIgnoreCase(voice)
                || voiceFilterTokens(item.voice()).stream()
                .anyMatch(token -> normalizedVoiceToken(token).equals(normalizedVoiceToken(voice)));
    }

    private List<String> voiceFilterTokens(String voice) {
        if (voice == null || voice.isBlank()) {
            return List.of(TorrentAvailabilityItem.UNKNOWN_VOICE);
        }
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        canonicalVoiceToken(voice).ifPresent(tokens::add);
        tokens.addAll(voiceParts(voice).stream()
                .map(this::canonicalVoiceToken)
                .flatMap(Optional::stream)
                .toList());
        tokens = new java.util.ArrayList<>(tokens.stream().distinct().toList());
        if (tokens.isEmpty()) {
            return List.of(TorrentAvailabilityItem.UNKNOWN_VOICE);
        }
        return List.copyOf(tokens);
    }

    private List<String> voiceParts(String voice) {
        return java.util.Arrays.stream(voice.split("\\+"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .distinct()
                .toList();
    }

    private Optional<String> canonicalVoiceToken(String voicePart) {
        String normalized = normalizeVoiceValue(voicePart);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (normalized.equals(normalizeVoiceValue(TorrentAvailabilityItem.UNKNOWN_VOICE))) {
            return Optional.of(TorrentAvailabilityItem.UNKNOWN_VOICE);
        }
        if (normalized.contains("lostfilm") || normalized.contains("lost film")) {
            return Optional.of(VOICE_LOSTFILM);
        }
        if (normalized.contains("videofilm") || normalized.contains("video film")) {
            return Optional.of(VOICE_VIDEOFILM);
        }
        if (normalized.contains("ukr") && normalized.contains("eng")) {
            return Optional.of(VOICE_UKR_ENG);
        }
        if (normalized.equals(normalizeVoiceValue(VOICE_DUB)) || normalized.equals("dub") || normalized.equals("dubbed")) {
            return Optional.of(VOICE_DUB);
        }
        if (isNonSelectableVoicePart(normalized)) {
            return Optional.empty();
        }
        return Optional.of(voicePart.trim());
    }

    private String normalizedVoiceToken(String voice) {
        return canonicalVoiceToken(voice)
                .map(this::normalizeVoiceValue)
                .orElse("");
    }

    private String normalizeVoiceValue(String voice) {
        if (voice == null) {
            return "";
        }
        return voice.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}/]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private boolean isNonSelectableVoicePart(String voicePart) {
        return voicePart.equals("субтитры")
                || voicePart.equals("sub")
                || voicePart.equals("subs")
                || voicePart.equals("subtitles")
                || voicePart.equals("original")
                || voicePart.equals("оригинал");
    }

    private int voicePriority(String voice) {
        int index = VOICE_PRIORITY.indexOf(voice);
        return index >= 0 ? index : VOICE_PRIORITY.size();
    }

    private List<TorrentAvailabilityItem> distinctItems(List<VoiceItem> voiceItems) {
        return voiceItems.stream()
                .map(VoiceItem::item)
                .distinct()
                .toList();
    }

    private boolean matchesQuality(TorrentAvailabilityItem item, String quality) {
        if (quality == null || quality.isBlank() || ANY_QUALITY.equalsIgnoreCase(quality)) {
            return true;
        }
        if (QUALITY_OPTIMAL.equalsIgnoreCase(quality)) {
            return isOptimalQuality(item.quality());
        }
        if (QUALITY_SMALL.equalsIgnoreCase(quality)) {
            return isSmallQuality(item.quality());
        }
        if (QUALITY_MAX.equalsIgnoreCase(quality)) {
            return isMaxQuality(item.quality());
        }
        return item.quality().equalsIgnoreCase(quality);
    }

    private void addQualityGroup(java.util.ArrayList<QualityOption> options, List<TorrentAvailabilityItem> scopedItems, String group) {
        List<TorrentAvailabilityItem> matchingItems = scopedItems.stream()
                .filter(item -> matchesQuality(item, group))
                .toList();
        if (matchingItems.isEmpty()) {
            return;
        }
        options.add(new QualityOption(
                group,
                matchingItems.size(),
                matchingItems.stream().mapToInt(TorrentAvailabilityItem::seeders).max().orElse(0)
        ));
    }

    private boolean isOptimalQuality(String quality) {
        String normalized = normalizedQuality(quality);
        return normalized.contains("1080p")
                && (normalized.contains("web-dl") || normalized.contains("webrip") || normalized.contains("bdrip"));
    }

    private boolean isSmallQuality(String quality) {
        String normalized = normalizedQuality(quality);
        return normalized.contains("720p")
                || (normalized.contains("1080p") && !normalized.contains("remux") && !normalized.contains("2160p"));
    }

    private boolean isMaxQuality(String quality) {
        String normalized = normalizedQuality(quality);
        return normalized.contains("2160p") || normalized.contains("4k") || normalized.contains("remux");
    }

    private String normalizedQuality(String quality) {
        return quality == null ? "" : quality.toLowerCase(Locale.ROOT);
    }

    private Comparator<TorrentAvailabilityItem> itemComparator(Integer selectedSeason) {
        return Comparator
                .comparingInt((TorrentAvailabilityItem item) -> releaseRank(item, selectedSeason))
                .thenComparing(TorrentAvailabilityItem::seeders, Comparator.reverseOrder())
                .thenComparing((TorrentAvailabilityItem item) -> parsedDate(item.publishDate()), Comparator.reverseOrder())
                .thenComparing(item -> item.result().peers(), Comparator.reverseOrder())
                .thenComparing(item -> item.result().sizeBytes(), Comparator.reverseOrder());
    }

    private int releaseRank(TorrentAvailabilityItem item, Integer selectedSeason) {
        if (selectedSeason == null) {
            return 0;
        }
        if (item.releaseType() == ReleaseType.SEASON_PACK && Objects.equals(item.seasonNumber(), selectedSeason)) {
            return 0;
        }
        if (item.releaseType() == ReleaseType.EPISODE || item.releaseType() == ReleaseType.EPISODE_RANGE) {
            return 1;
        }
        if (item.releaseType() == ReleaseType.MULTI_SEASON_PACK) {
            return 2;
        }
        return 3;
    }

    private OffsetDateTime parsedDate(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.MIN;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.MIN;
        }
    }

    public record SeasonOption(int seasonNumber, int resultCount, int maxSeeders) {
    }

    public record ScopeOption(String code, String label, int resultCount, int maxSeeders) {
    }

    public record VoiceOption(String voice, int resultCount, int maxSeeders) {
    }

    public record QualityOption(String quality, int resultCount, int maxSeeders) {
    }

    private record VoiceItem(String voice, TorrentAvailabilityItem item) {
    }
}
