package ru.xataaa.torrentbot.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SafeLog {

    private SafeLog() {
    }

    public static String preview(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String singleLine = value.replace("\n", " ").replace("\r", " ").trim();
        if (singleLine.length() <= maxLength) {
            return singleLine;
        }
        return singleLine.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public static String sha256Short(String value) {
        if (value == null) {
            return "";
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
