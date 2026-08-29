package ru.xataaa.torrentbot.movie;

public record MovieMetadata(
        String selectionId,
        String tmdbId,
        MovieMediaType mediaType,
        String title,
        String originalTitle,
        Integer year,
        Double rating,
        String overview,
        String posterUrl
) {
    public boolean isTv() {
        return mediaType == MovieMediaType.TV;
    }
}
