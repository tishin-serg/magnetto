package ru.xataaa.torrentbot.application;

public interface DownloadNotifier {
    void accepted(Long chatId, java.util.UUID jobId);
}
