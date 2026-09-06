package ru.xataaa.torrentbot.preferences;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.xataaa.torrentbot.job.DownloadTarget;

@Repository
@RequiredArgsConstructor
public class DownloadPreferencesRepository {
    private final JdbcTemplate jdbc;

    public DownloadPreferences find(long chatId) {
        return jdbc.query("select * from download_preferences where chat_id = ?", (rs, row) ->
                new DownloadPreferences(rs.getLong("min_bytes"), rs.getLong("max_bytes"),
                        rs.getInt("min_seeders"), DownloadTarget.valueOf(rs.getString("target"))), chatId)
                .stream().findFirst().orElseGet(DownloadPreferences::defaults);
    }

    public void save(long chatId, DownloadPreferences preferences) {
        jdbc.update("""
                insert into download_preferences(chat_id,min_bytes,max_bytes,min_seeders,target)
                values (?,?,?,?,?) on conflict(chat_id) do update set
                min_bytes=excluded.min_bytes,max_bytes=excluded.max_bytes,
                min_seeders=excluded.min_seeders,target=excluded.target
                """, chatId, preferences.minBytes(), preferences.maxBytes(), preferences.minSeeders(), preferences.target().name());
    }
}