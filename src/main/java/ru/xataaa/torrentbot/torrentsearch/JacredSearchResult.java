package ru.xataaa.torrentbot.torrentsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JacredSearchResult {

    @JsonProperty("Tracker")
    private String tracker;

    @JsonProperty("Title")
    private String title;

    @JsonProperty("Details")
    private String details;

    @JsonProperty("MagnetUri")
    private String magnetUri;

    @JsonProperty("Link")
    private String link;

    @JsonProperty("Size")
    private Long size;

    @JsonProperty("Seeders")
    private Integer seeders;

    @JsonProperty("Peers")
    private Integer peers;

    @JsonProperty("PublishDate")
    private String publishDate;

    @JsonProperty("Category")
    private List<Integer> category;
}
