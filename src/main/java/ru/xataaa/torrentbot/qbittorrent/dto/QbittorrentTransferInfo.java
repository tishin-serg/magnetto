package ru.xataaa.torrentbot.qbittorrent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QbittorrentTransferInfo {

    @JsonProperty("free_space_on_disk")
    private Long freeSpaceOnDisk;
}
