package ru.xataaa.torrentbot.torrentsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JacredSearchResponse {

    @JsonProperty("Results")
    private List<JacredSearchResult> results;
}
