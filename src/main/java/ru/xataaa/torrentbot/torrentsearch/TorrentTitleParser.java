package ru.xataaa.torrentbot.torrentsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TorrentTitleParser {

    private static final Pattern SXX_EXX_PATTERN = Pattern.compile("(?iu)(?<![a-zа-я0-9])s(\\d{1,2})\\s*e(\\d{1,3})(?:\\s*[-–]\\s*e?(\\d{1,3}))?(?![a-zа-я0-9])");
    private static final Pattern X_PATTERN = Pattern.compile("(?iu)(?<!\\d)(\\d{1,2})\\s*x\\s*(\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?(?!\\d)");
    private static final Pattern SXX_RANGE_PATTERN = Pattern.compile("(?iu)(?<![a-zа-я0-9])s(\\d{1,2})\\s*[-–]\\s*s?(\\d{1,2})(?![a-zа-я0-9])");
    private static final Pattern SXX_PATTERN = Pattern.compile("(?iu)(?<![a-zа-я0-9])s(\\d{1,2})(?!\\s*e\\d)(?!\\s*[-–]\\s*s?\\d)(?![a-zа-я0-9])");
    private static final Pattern SEASON_RANGE_WORD_PATTERN = Pattern.compile("(?iu)(?:сезон(?:ы|ов)?|season(?:s)?)\\s*:?\\s*(\\d{1,2})\\s*[-–]\\s*(\\d{1,2})|(?<!\\d)(\\d{1,2})\\s*[-–]\\s*(\\d{1,2})\\s*(?:сезон(?:ы|ов)?|season(?:s)?)(?!\\d)");
    private static final Pattern SEASON_WORD_PATTERN = Pattern.compile("(?iu)(?:сезон|season)\\s*:?\\s*(\\d{1,2})|(?<!\\d)(\\d{1,2})\\s*(?:сезон|season)(?!\\d)");
    private static final Pattern EPISODE_WORD_PATTERN = Pattern.compile("(?iu)(?:серии?|episodes?)\\s*:?\\s*(\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?(?:\\s*из\\s*(\\d{1,3}))?|(?<!\\d)(\\d{1,3})\\s*[-–]\\s*(\\d{1,3})\\s*(?:серии?|episodes?)(?!\\d)");
    private static final Pattern VOICE_TAG_PATTERN = Pattern.compile("(?iu)(?:^|[\\s/|,])(?:ПМ|МВО|MVO|АП|AП|ДБ|СТ|DUB|DUBBED|VO)\\s*\\(([^)]+)\\)(?:\\s*\\+\\s*(Original|Оригинал))?");
    private static final Pattern PAREN_PATTERN = Pattern.compile("[\\[({]([^\\])}]{2,80})[\\])}]");
    private static final Pattern YEAR_OR_RANGE_PATTERN = Pattern.compile("^(?:19|20)\\d{2}(?:\\s*[-–]\\s*(?:19|20)\\d{2})?$");
    private static final Pattern SEASON_MARKER_PATTERN = Pattern.compile("(?iu).*\\b(?:s\\d{1,2}|season\\s*\\d{1,2}|сезон\\s*\\d{1,2}|\\d{1,2}\\s*сезон).*");

    public ParsedTorrentTitle parse(TorrentSearchResult result) {
        String title = result == null || result.title() == null ? "" : result.title();
        SeasonRange seasonRange = detectSeasonRange(title);
        EpisodeRange episodeRange = detectEpisodeRange(title);
        ReleaseType releaseType = detectReleaseType(seasonRange, episodeRange);

        return new ParsedTorrentTitle(
                seasonRange,
                episodeRange,
                releaseType,
                detectQuality(title),
                detectVoice(title)
        );
    }

    private SeasonRange detectSeasonRange(String text) {
        Matcher episodeSeasonMatcher = SXX_EXX_PATTERN.matcher(text);
        if (episodeSeasonMatcher.find()) {
            return parseSeasonRange(episodeSeasonMatcher.group(1), episodeSeasonMatcher.group(1)).orElse(null);
        }
        Matcher xMatcher = X_PATTERN.matcher(text);
        if (xMatcher.find()) {
            return parseSeasonRange(xMatcher.group(1), xMatcher.group(1)).orElse(null);
        }
        Matcher sxxRangeMatcher = SXX_RANGE_PATTERN.matcher(text);
        if (sxxRangeMatcher.find()) {
            return parseSeasonRange(sxxRangeMatcher.group(1), sxxRangeMatcher.group(2)).orElse(null);
        }
        SeasonRange singleSeason = detectSingleSeasonBeforeRange(text);
        if (singleSeason != null) {
            return singleSeason;
        }
        Matcher seasonRangeWordMatcher = SEASON_RANGE_WORD_PATTERN.matcher(text);
        if (seasonRangeWordMatcher.find()) {
            String start = firstNonNull(seasonRangeWordMatcher.group(1), seasonRangeWordMatcher.group(3));
            String end = firstNonNull(seasonRangeWordMatcher.group(2), seasonRangeWordMatcher.group(4));
            return parseSeasonRange(start, end).orElse(null);
        }
        Matcher sxxMatcher = SXX_PATTERN.matcher(text);
        if (sxxMatcher.find()) {
            return parseSeasonRange(sxxMatcher.group(1), sxxMatcher.group(1)).orElse(null);
        }
        Matcher seasonWordMatcher = SEASON_WORD_PATTERN.matcher(text);
        if (seasonWordMatcher.find()) {
            String season = firstNonNull(seasonWordMatcher.group(1), seasonWordMatcher.group(2));
            return parseSeasonRange(season, season).orElse(null);
        }
        return null;
    }

    private SeasonRange detectSingleSeasonBeforeRange(String text) {
        Matcher seasonWordMatcher = SEASON_WORD_PATTERN.matcher(text);
        while (seasonWordMatcher.find()) {
            String season = firstNonNull(seasonWordMatcher.group(1), seasonWordMatcher.group(2));
            if (season == null) {
                continue;
            }
            int start = seasonWordMatcher.group(1) != null ? seasonWordMatcher.start(1) : seasonWordMatcher.start(2);
            int end = seasonWordMatcher.group(1) != null ? seasonWordMatcher.end(1) : seasonWordMatcher.end(2);
            if (hasRangeMarkerBefore(text, start) || hasRangeMarkerAfter(text, end)) {
                continue;
            }
            return parseSeasonRange(season, season).orElse(null);
        }
        return null;
    }

    private EpisodeRange detectEpisodeRange(String text) {
        Matcher sxxExxMatcher = SXX_EXX_PATTERN.matcher(text);
        if (sxxExxMatcher.find()) {
            return parseEpisodeRange(sxxExxMatcher.group(2), firstNonNull(sxxExxMatcher.group(3), sxxExxMatcher.group(2))).orElse(null);
        }
        Matcher xMatcher = X_PATTERN.matcher(text);
        if (xMatcher.find()) {
            return parseEpisodeRange(xMatcher.group(2), firstNonNull(xMatcher.group(3), xMatcher.group(2))).orElse(null);
        }
        Matcher episodeWordMatcher = EPISODE_WORD_PATTERN.matcher(text);
        if (episodeWordMatcher.find()) {
            String start = firstNonNull(episodeWordMatcher.group(1), episodeWordMatcher.group(4));
            String end = firstNonNull(episodeWordMatcher.group(2), episodeWordMatcher.group(3), episodeWordMatcher.group(5), start);
            return parseEpisodeRange(start, end).orElse(null);
        }
        return null;
    }

    private ReleaseType detectReleaseType(SeasonRange seasonRange, EpisodeRange episodeRange) {
        if (seasonRange == null) {
            return ReleaseType.MOVIE;
        }
        if (seasonRange.multiSeason()) {
            return ReleaseType.MULTI_SEASON_PACK;
        }
        if (episodeRange == null) {
            return ReleaseType.SEASON_PACK;
        }
        if (episodeRange.start() == 1 && episodeRange.end() >= 6) {
            return ReleaseType.SEASON_PACK;
        }
        return episodeRange.multiEpisode() ? ReleaseType.EPISODE_RANGE : ReleaseType.EPISODE;
    }

    public String detectQuality(String text) {
        if (text == null || text.isBlank()) {
            return "Unknown";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        String source = "";
        if (normalized.contains("bdremux") || normalized.contains("remux")) {
            source = "BDRemux";
        } else if (normalized.contains("bdrip")) {
            source = "BDRip";
        } else if (normalized.contains("bluray") || normalized.contains("blu-ray")) {
            source = "BluRay";
        } else if (normalized.contains("web-dl") || normalized.contains("webdl")) {
            source = "WEB-DL";
        } else if (normalized.contains("webrip")) {
            source = "WEBRip";
        } else if (normalized.contains("hdrip")) {
            source = "HDRip";
        } else if (normalized.contains("hdtvrip")) {
            source = "HDTVRip";
        }

        String resolution = "";
        if (normalized.contains("2160p") || normalized.contains("4k") || normalized.contains("uhd")) {
            resolution = "2160p";
        } else if (normalized.contains("1080p")) {
            resolution = "1080p";
        } else if (normalized.contains("720p")) {
            resolution = "720p";
        }

        if (!source.isBlank() && !resolution.isBlank()) {
            return source + " " + resolution;
        }
        if (!source.isBlank()) {
            return source;
        }
        if (!resolution.isBlank()) {
            return resolution;
        }
        return "Unknown";
    }

    private String detectVoice(String title) {
        if (title == null || title.isBlank()) {
            return TorrentAvailabilityItem.UNKNOWN_VOICE;
        }

        List<String> voices = new ArrayList<>();
        Matcher taggedVoiceMatcher = VOICE_TAG_PATTERN.matcher(title);
        while (taggedVoiceMatcher.find()) {
            addVoice(voices, taggedVoiceMatcher.group(1));
            addVoice(voices, taggedVoiceMatcher.group(2));
        }

        if (containsToken(title, "Original") || containsToken(title, "Оригинал")) {
            addVoice(voices, "Original");
        }
        if (containsToken(title, "Дубляж") || containsToken(title, "ДБ")) {
            addVoice(voices, "Дубляж");
        }
        if (containsToken(title, "СТ") || containsToken(title, "Субтитры")) {
            addVoice(voices, "Субтитры");
        }
        if (Pattern.compile("(?iu)(?:^|[\\s/|,])Ukr\\s*/\\s*Eng(?:$|[\\s/|,])").matcher(title).find()) {
            addVoice(voices, "Ukr/Eng");
        }

        for (String knownVoice : knownVoices()) {
            if (containsToken(title, knownVoice)) {
                addVoice(voices, knownVoice);
            }
        }

        Matcher parenMatcher = PAREN_PATTERN.matcher(title);
        while (parenMatcher.find()) {
            String candidate = parenMatcher.group(1).trim();
            if (isSafeStandaloneVoice(candidate)) {
                addVoice(voices, candidate);
            }
        }

        if (voices.isEmpty()) {
            return TorrentAvailabilityItem.UNKNOWN_VOICE;
        }
        return String.join(" + ", voices.stream().distinct().toList());
    }

    private boolean isSafeStandaloneVoice(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String normalized = candidate.trim().toLowerCase(Locale.ROOT);
        if (YEAR_OR_RANGE_PATTERN.matcher(normalized).matches()
                || SEASON_MARKER_PATTERN.matcher(normalized).matches()
                || !detectQuality(candidate).equals("Unknown")
                || normalized.matches(".*\\d{3,4}p.*")) {
            return false;
        }
        if (SetLikeWords.NOISE_WORDS.stream().anyMatch(normalized::contains)) {
            return false;
        }
        return knownVoices().stream().anyMatch(known -> known.equalsIgnoreCase(candidate.trim()));
    }

    private void addVoice(List<String> voices, String candidate) {
        normalizeVoice(candidate).ifPresent(voice -> {
            if (!voices.contains(voice)) {
                voices.add(voice);
            }
        });
    }

    private Optional<String> normalizeVoice(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        String value = candidate.trim()
                .replaceAll("(?iu)^\\s*(ПМ|МВО|MVO|АП|AП|ДБ|СТ|DUB|DUBBED|VO)\\s*", "")
                .replaceAll("\\s+", " ");
        String normalized = value.toLowerCase(Locale.ROOT);
        if (YEAR_OR_RANGE_PATTERN.matcher(normalized).matches()
                || SEASON_MARKER_PATTERN.matcher(normalized).matches()
                || SetLikeWords.NOISE_WORDS.stream().anyMatch(normalized::contains)
                || !detectQuality(value).equals("Unknown")) {
            return Optional.empty();
        }
        if (normalized.equals("original") || normalized.equals("оригинал")) {
            return Optional.of("Original");
        }
        if (normalized.equals("bti studios") || normalized.equals("bti")) {
            return Optional.of("BTI Studios");
        }
        if (normalized.equals("пифагор") || normalized.equals("pifagor")) {
            return Optional.of("Пифагор");
        }
        if (normalized.equals("дб") || normalized.equals("дубляж")) {
            return Optional.of("Дубляж");
        }
        if (normalized.equals("ст") || normalized.equals("субтитры")) {
            return Optional.of("Субтитры");
        }
        return Optional.of(value);
    }

    private boolean containsToken(String text, String token) {
        return Pattern.compile("(?iu)(?<![a-zа-я0-9])" + Pattern.quote(token) + "(?![a-zа-я0-9])")
                .matcher(text)
                .find();
    }

    private List<String> knownVoices() {
        return List.of(
                "LostFilm", "TVShows", "Amedia", "HDRezka", "NewStudio", "Сербин", "Первый канал", "Арк-ТВ",
                "Пифагор", "BTI Studios", "Jaskier", "Кубик в Кубе", "Кравец", "BaibaKo", "ColdFilm", "RuDub"
        );
    }

    private Optional<SeasonRange> parseSeasonRange(String startValue, String endValue) {
        Optional<Integer> start = parseInt(startValue);
        Optional<Integer> end = parseInt(endValue);
        if (start.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SeasonRange(start.get(), end.get()));
    }

    private Optional<EpisodeRange> parseEpisodeRange(String startValue, String endValue) {
        Optional<Integer> start = parseInt(startValue);
        Optional<Integer> end = parseInt(endValue);
        if (start.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EpisodeRange(start.get(), end.get()));
    }

    private Optional<Integer> parseInt(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean hasRangeMarkerBefore(String text, int index) {
        int previous = index - 1;
        while (previous >= 0 && Character.isWhitespace(text.charAt(previous))) {
            previous--;
        }
        return previous >= 0 && (text.charAt(previous) == '-' || text.charAt(previous) == '–');
    }

    private boolean hasRangeMarkerAfter(String text, int index) {
        int next = index;
        while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
            next++;
        }
        return next < text.length() && (text.charAt(next) == '-' || text.charAt(next) == '–');
    }

    private static class SetLikeWords {
        private static final List<String> NOISE_WORDS = List.of(
                "сша", "драма", "криминал", "комедия", "боевик", "триллер", "детектив",
                "boardwalk empire", "преступная империя", "подпольная империя", "usa"
        );
    }

    public record ParsedTorrentTitle(
            SeasonRange seasonRange,
            EpisodeRange episodeRange,
            ReleaseType releaseType,
            String quality,
            String voice
    ) {
        public Integer seasonNumber() {
            return seasonRange == null || seasonRange.multiSeason() ? null : seasonRange.start();
        }

        public java.util.Set<Integer> seasonNumbers() {
            return seasonRange == null ? java.util.Set.of() : seasonRange.seasons();
        }

        public java.util.Set<Integer> episodeNumbers() {
            return episodeRange == null ? java.util.Set.of() : episodeRange.episodes();
        }

        public boolean seasonPack() {
            return releaseType == ReleaseType.SEASON_PACK;
        }
    }
}
