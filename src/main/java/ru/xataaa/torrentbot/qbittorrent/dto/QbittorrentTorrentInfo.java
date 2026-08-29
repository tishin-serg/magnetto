package ru.xataaa.torrentbot.qbittorrent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QbittorrentTorrentInfo {
    private String hash;
    private String name;
    private double progress;
    private String state;
    private String tags;
    @JsonProperty("total_size")
    private long totalSize;
    private long downloaded;
    @JsonProperty("amount_left")
    private long amountLeft;
    @JsonProperty("dlspeed")
    private long downloadSpeed;
    private long eta;

    public boolean hasMetadata() {
        return name != null && !name.isBlank() && !"metaDL".equalsIgnoreCase(state);
    }
}
