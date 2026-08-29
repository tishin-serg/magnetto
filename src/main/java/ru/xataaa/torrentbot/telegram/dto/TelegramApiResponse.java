package ru.xataaa.torrentbot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramApiResponse<T> {
    private boolean ok;
    private T result;
    private String description;
    @JsonProperty("error_code")
    private Integer errorCode;
}
