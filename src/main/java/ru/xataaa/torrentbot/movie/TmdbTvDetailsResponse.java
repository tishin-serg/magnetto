package ru.xataaa.torrentbot.movie;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbTvDetailsResponse {

    private List<TmdbSeason> seasons;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbSeason {
        private String name;

        @JsonProperty("season_number")
        private Integer seasonNumber;

        @JsonProperty("episode_count")
        private Integer episodeCount;
    }
}
