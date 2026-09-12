package ru.xataaa.torrentbot.qbittorrent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QbittorrentMainData {

    @JsonProperty("server_state")
    private ServerState serverState;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerState {

        @JsonProperty("free_space_on_disk")
        private Long freeSpaceOnDisk;
    }
}
