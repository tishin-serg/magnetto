package ru.xataaa.torrentbot.common;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class TimeProvider {

    public static final ZoneId MOSCOW_ZONE_ID = ZoneId.of("Europe/Moscow");

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm 'МСК'");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM HH:mm 'МСК'");

    public LocalDateTime now() {
        return LocalDateTime.now(MOSCOW_ZONE_ID);
    }

    public ZonedDateTime zonedNow() {
        return ZonedDateTime.now(MOSCOW_ZONE_ID);
    }

    public String formatTime(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return "";
        }
        return localDateTime.format(TIME_FORMATTER);
    }

    public String formatDateTime(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return "";
        }
        return localDateTime.format(DATE_TIME_FORMATTER);
    }
}
