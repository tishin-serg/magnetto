package ru.xataaa.torrentbot.qbittorrent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QbittorrentTorrentFile {
    private int index;
    private String name;
    private long size;
    private double progress;
    private int priority;
}
