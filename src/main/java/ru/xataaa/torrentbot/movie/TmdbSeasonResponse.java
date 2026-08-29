package ru.xataaa.torrentbot.movie;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbSeasonResponse {

    private String name;

    @JsonProperty("season_number")
    private Integer seasonNumber;

    private List<TmdbEpisode> episodes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbEpisode {
        private String name;

        @JsonProperty("episode_number")
        private Integer episodeNumber;
    }
}
