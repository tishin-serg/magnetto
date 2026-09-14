package ru.xataaa.torrentbot.application;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ShortcutPreferencesRepository {
    private final JdbcTemplate jdbc;
    public UUID ensureUser(UUID userId, Long telegramChatId) {
        if (telegramChatId != null) {
            Optional<UUID> migratedUser = findByTelegramChatId(telegramChatId);
            if (migratedUser.isPresent()) {
                return migratedUser.get();
            }
        }
        jdbc.update("insert into app_user(id,telegram_chat_id) values(?,?) on conflict do nothing", userId, telegramChatId);
        return telegramChatId == null ? userId : findByTelegramChatId(telegramChatId).orElse(userId);
    }
    private Optional<UUID> findByTelegramChatId(Long telegramChatId) {
        return jdbc.query("select id from app_user where telegram_chat_id=?", (rs,n) -> rs.getObject(1, UUID.class), telegramChatId)
                .stream().findFirst();
    }
    public ShortcutPreferences find(UUID userId) {
        return jdbc.query("select min_bytes,max_bytes,min_seeders,quality,voice,min_speed_bps,auto_replace_slow_download from shortcut_preferences where user_id=?", (rs,n) -> new ShortcutPreferences(rs.getLong(1),rs.getLong(2),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getLong(6),rs.getBoolean(7)), userId).stream().findFirst().orElseGet(ShortcutPreferences::defaults);
    }
    public void save(UUID userId, ShortcutPreferences p) {
        ensureUser(userId, null);
        jdbc.update("insert into shortcut_preferences(user_id,min_bytes,max_bytes,min_seeders,quality,voice,min_speed_bps,auto_replace_slow_download,updated_at) values(?,?,?,?,?,?,?,?,?) on conflict(user_id) do update set min_bytes=excluded.min_bytes,max_bytes=excluded.max_bytes,min_seeders=excluded.min_seeders,quality=excluded.quality,voice=excluded.voice,min_speed_bps=excluded.min_speed_bps,auto_replace_slow_download=excluded.auto_replace_slow_download,updated_at=excluded.updated_at", userId,p.minBytes(),p.maxBytes(),p.minSeeders(),p.quality(),p.voice(),p.minSpeedBytesPerSecond(),p.autoReplaceSlowDownload(),LocalDateTime.now());
    }
}
