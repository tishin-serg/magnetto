package ru.xataaa.torrentbot.torrentsearch;

public record TorrentSearchRequest(
        String title,
        String originalTitle,
        Integer year,
        Integer serialType,
        String query,
        TorrentSearchFilters filters
) {
    public static TorrentSearchRequest fromMovie(String title, String originalTitle, Integer year, boolean serial) {
        return fromMovie(title, originalTitle, year, serial, TorrentSearchFilters.any());
    }

    public static TorrentSearchRequest fromMovie(
            String title,
            String originalTitle,
            Integer year,
            boolean serial,
            TorrentSearchFilters filters
    ) {
        TorrentSearchFilters safeFilters = filters == null ? TorrentSearchFilters.any() : filters;
        String safeTitle = title == null ? "" : title.trim();
        String safeOriginalTitle = originalTitle == null ? "" : originalTitle.trim();
        StringBuilder query = new StringBuilder(safeTitle);
        if (year != null) {
            query.append(" ").append(year);
        }
        if (safeFilters.hasEpisodeSelection()) {
            Integer firstEpisodeNumber = safeFilters.episodeNumbers().stream().findFirst().orElse(null);
            if (firstEpisodeNumber != null) {
                query.append(" ").append(episodePattern(safeFilters.seasonNumber(), firstEpisodeNumber));
            }
        }
        return new TorrentSearchRequest(
                safeTitle,
                safeOriginalTitle,
                year,
                serial ? 2 : 1,
                query.toString().trim(),
                safeFilters
        );
    }

    public static TorrentSearchRequest fromUserText(String text) {
        String normalizedText = text == null ? "" : text.trim();
        String query = normalizedText;
        Integer year = extractYear(normalizedText);
        if (year != null) {
            query = normalizedText.replace(year.toString(), "").trim();
        }
        Integer serialType = normalizedText.toLowerCase().contains("сериал") ? 2 : 1;
        return new TorrentSearchRequest(query, null, year, serialType, normalizedText, TorrentSearchFilters.any());
    }

    private static Integer extractYear(String text) {
        String[] parts = text.split("\\D+");
        for (String part : parts) {
            if (part.length() == 4) {
                int value = Integer.parseInt(part);
                if (value >= 1900 && value <= 2100) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String episodePattern(Integer seasonNumber, Integer episodeNumber) {
        if (seasonNumber == null || episodeNumber == null) {
            return "";
        }
        return "S" + twoDigits(seasonNumber) + "E" + twoDigits(episodeNumber);
    }

    private static String twoDigits(Integer value) {
        if (value == null) {
            return "00";
        }
        return value < 10 ? "0" + value : value.toString();
    }
}
