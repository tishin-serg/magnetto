package ru.xataaa.torrentbot.downloadlink;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcHomeDownloadLinkRepository implements HomeDownloadLinkRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void save(HomeDownloadLink homeDownloadLink) {
        String sql = """
                insert into home_download_link (
                    id, token, chat_id, file_name, file_size_bytes, status, expires_at, created_at, used_at
                ) values (
                    :id, :token, :chatId, :fileName, :fileSizeBytes, :status, :expiresAt, :createdAt, :usedAt
                )
                """;
        jdbcTemplate.update(sql, toParameters(homeDownloadLink));
    }

    @Override
    public Optional<HomeDownloadLink> findByToken(String token) {
        List<HomeDownloadLink> links = jdbcTemplate.query(
                "select * from home_download_link where token = :token",
                new MapSqlParameterSource("token", token),
                this::mapRow
        );
        return links.stream().findFirst();
    }

    @Override
    public List<HomeDownloadLink> findExpiredActiveLinks(LocalDateTime now) {
        return jdbcTemplate.query(
                "select * from home_download_link where status = 'ACTIVE' and expires_at <= :now order by expires_at",
                new MapSqlParameterSource("now", now),
                this::mapRow
        );
    }

    @Override
    public void updateStatus(UUID id, DownloadLinkStatus status) {
        jdbcTemplate.update(
                "update home_download_link set status = :status where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status.name())
        );
    }

    @Override
    public void markUsed(UUID id, LocalDateTime usedAt) {
        jdbcTemplate.update(
                "update home_download_link set used_at = :usedAt where id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("usedAt", usedAt)
        );
    }

    private MapSqlParameterSource toParameters(HomeDownloadLink homeDownloadLink) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", homeDownloadLink.getId());
        values.put("token", homeDownloadLink.getToken());
        values.put("chatId", homeDownloadLink.getChatId());
        values.put("fileName", homeDownloadLink.getFileName());
        values.put("fileSizeBytes", homeDownloadLink.getFileSizeBytes());
        values.put("status", homeDownloadLink.getStatus().name());
        values.put("expiresAt", homeDownloadLink.getExpiresAt());
        values.put("createdAt", homeDownloadLink.getCreatedAt());
        values.put("usedAt", homeDownloadLink.getUsedAt());
        return new MapSqlParameterSource(values);
    }

    private HomeDownloadLink mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return HomeDownloadLink.builder()
                .id(resultSet.getObject("id", UUID.class))
                .token(resultSet.getString("token"))
                .chatId(resultSet.getLong("chat_id"))
                .fileName(resultSet.getString("file_name"))
                .fileSizeBytes(resultSet.getLong("file_size_bytes"))
                .status(DownloadLinkStatus.valueOf(resultSet.getString("status")))
                .expiresAt(resultSet.getTimestamp("expires_at").toLocalDateTime())
                .createdAt(resultSet.getTimestamp("created_at").toLocalDateTime())
                .usedAt(toLocalDateTime(resultSet.getTimestamp("used_at")))
                .build();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
