package ru.xataaa.torrentbot.movie;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbSearchResponse {
    private List<TmdbSearchResult> results;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbSearchResult {
        private Long id;

        @JsonProperty("media_type")
        private String mediaType;

        private String title;
        private String name;

        @JsonProperty("original_title")
        private String originalTitle;

        @JsonProperty("original_name")
        private String originalName;

        @JsonProperty("release_date")
        private String releaseDate;

        @JsonProperty("first_air_date")
        private String firstAirDate;

        @JsonProperty("vote_average")
        private Double voteAverage;

        private String overview;

        @JsonProperty("poster_path")
        private String posterPath;
    }
}
