package ru.xataaa.torrentbot.common;

import org.springframework.stereotype.Component;

@Component
public class MagnetValidator {

    private static final String MAGNET_PREFIX = "magnet:?xt=urn:btih:";

    public boolean isValid(String text) {
        if (text == null) {
            return false;
        }
        return text.trim().toLowerCase().startsWith(MAGNET_PREFIX);
    }
}
