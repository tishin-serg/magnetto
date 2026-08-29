package ru.xataaa.torrentbot.downloadlink;

import java.time.LocalDateTime;
import ru.xataaa.torrentbot.common.TimeProvider;

class FixedTimeProvider extends TimeProvider {

    private LocalDateTime now;

    FixedTimeProvider(LocalDateTime now) {
        this.now = now;
    }

    @Override
    public LocalDateTime now() {
        return now;
    }

    void setNow(LocalDateTime now) {
        this.now = now;
    }
}
