package ru.xataaa.torrentbot.downloadlink;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.config.TelegramProperties;

@Service
@RequiredArgsConstructor
public class FileDeliveryDecisionService {

    private final TelegramProperties telegramProperties;

    public FileDeliveryMode decide(long fileSizeBytes) {
        if (fileSizeBytes <= telegramProperties.file().directSendLimitBytes()) {
            return FileDeliveryMode.TELEGRAM_DIRECT;
        }
        return FileDeliveryMode.WEBDAV_LIBRARY;
    }
}
