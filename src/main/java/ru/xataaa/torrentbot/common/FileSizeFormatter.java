package ru.xataaa.torrentbot.common;

import org.springframework.stereotype.Component;

@Component
public class FileSizeFormatter {

    public String format(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        double sizeKilobytes = sizeBytes / 1024.0;
        if (sizeKilobytes < 1024) {
            return String.format("%.1f KB", sizeKilobytes);
        }
        double sizeMegabytes = sizeKilobytes / 1024.0;
        if (sizeMegabytes < 1024) {
            return String.format("%.1f MB", sizeMegabytes);
        }
        double sizeGigabytes = sizeMegabytes / 1024.0;
        return String.format("%.2f GB", sizeGigabytes);
    }
}
