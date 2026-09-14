package ru.xataaa.torrentbot.application;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ApiIdempotencyRepository {
    private final JdbcTemplate jdbc;
    public Optional<UUID> find(UUID userId, String key) {
        return jdbc.query("select job_id from api_idempotency_key where user_id=? and idempotency_key=?", (rs,n) -> rs.getObject(1, UUID.class), userId, key).stream().findFirst();
    }
    public boolean saveIfAbsent(UUID userId, String key, UUID jobId) {
        return jdbc.update("insert into api_idempotency_key(user_id,idempotency_key,job_id,created_at) values(?,?,?,?) on conflict(user_id,idempotency_key) do nothing", userId, key, jobId, LocalDateTime.now()) == 1;
    }
}
