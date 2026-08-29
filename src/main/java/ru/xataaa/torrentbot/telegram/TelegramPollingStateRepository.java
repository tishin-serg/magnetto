package ru.xataaa.torrentbot.telegram;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TelegramPollingStateRepository {

    private static final String TELEGRAM_OFFSET_KEY = "telegram.update.offset";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<Long> getOffset() {
        return jdbcTemplate.query(
                "select state_value from bot_state where state_key = :key",
                new MapSqlParameterSource("key", TELEGRAM_OFFSET_KEY),
                (resultSet, rowNumber) -> Long.parseLong(resultSet.getString("state_value"))
        ).stream().findFirst();
    }

    public void saveOffset(long offset) {
        jdbcTemplate.update(
                """
                insert into bot_state(state_key, state_value, updated_at)
                values (:key, :value, :now)
                on conflict (state_key) do update
                set state_value = excluded.state_value, updated_at = excluded.updated_at
                """,
                new MapSqlParameterSource()
                        .addValue("key", TELEGRAM_OFFSET_KEY)
                        .addValue("value", Long.toString(offset))
                        .addValue("now", LocalDateTime.now())
        );
    }
}
