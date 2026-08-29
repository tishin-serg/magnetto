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
public class JdbcDownloadLinkRepository implements DownloadLinkRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void save(DownloadLink downloadLink) {
        String sql = """
                insert into download_link (
                    id, job_id, file_id, token, chat_id, original_file_name, stored_file_name,
                    file_path, file_size_bytes, status, expires_at, created_at, used_at, deleted_at
                ) values (
                    :id, :jobId, :fileId, :token, :chatId, :originalFileName, :storedFileName,
                    :filePath, :fileSizeBytes, :status, :expiresAt, :createdAt, :usedAt, :deletedAt
                )
                """;
        jdbcTemplate.update(sql, toParameters(downloadLink));
    }

    @Override
    public Optional<DownloadLink> findByToken(String token) {
        List<DownloadLink> links = jdbcTemplate.query(
                "select * from download_link where token = :token",
                new MapSqlParameterSource("token", token),
                this::mapRow
        );
        return links.stream().findFirst();
    }

    @Override
    public Optional<DownloadLink> findActiveByFileId(UUID fileId) {
        List<DownloadLink> links = jdbcTemplate.query(
                "select * from download_link where file_id = :fileId and status = 'ACTIVE' order by created_at desc limit 1",
                new MapSqlParameterSource("fileId", fileId),
                this::mapRow
        );
        return links.stream().findFirst();
    }

    @Override
    public boolean existsActiveByJobId(UUID jobId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from download_link where job_id = :jobId and status = 'ACTIVE')",
                new MapSqlParameterSource("jobId", jobId),
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<DownloadLink> findExpiredActiveLinks(LocalDateTime now) {
        return jdbcTemplate.query(
                "select * from download_link where status = 'ACTIVE' and expires_at <= :now order by expires_at",
                new MapSqlParameterSource("now", now),
                this::mapRow
        );
    }

    @Override
    public List<DownloadLink> findByStatus(DownloadLinkStatus status) {
        return jdbcTemplate.query(
                "select * from download_link where status = :status order by expires_at",
                new MapSqlParameterSource("status", status.name()),
                this::mapRow
        );
    }

    @Override
    public void updateStatus(UUID id, DownloadLinkStatus status, LocalDateTime now) {
        String deletedAtExpression = status == DownloadLinkStatus.DELETED ? ", deleted_at = :now" : "";
        jdbcTemplate.update(
                "update download_link set status = :status" + deletedAtExpression + " where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status.name())
                        .addValue("now", now)
        );
    }

    @Override
    public void markUsed(UUID id, LocalDateTime usedAt) {
        jdbcTemplate.update(
                "update download_link set used_at = :usedAt where id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("usedAt", usedAt)
        );
    }

    private MapSqlParameterSource toParameters(DownloadLink downloadLink) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", downloadLink.getId());
        values.put("jobId", downloadLink.getJobId());
        values.put("fileId", downloadLink.getFileId());
        values.put("token", downloadLink.getToken());
        values.put("chatId", downloadLink.getChatId());
        values.put("originalFileName", downloadLink.getOriginalFileName());
        values.put("storedFileName", downloadLink.getStoredFileName());
        values.put("filePath", downloadLink.getFilePath());
        values.put("fileSizeBytes", downloadLink.getFileSizeBytes());
        values.put("status", downloadLink.getStatus().name());
        values.put("expiresAt", downloadLink.getExpiresAt());
        values.put("createdAt", downloadLink.getCreatedAt());
        values.put("usedAt", downloadLink.getUsedAt());
        values.put("deletedAt", downloadLink.getDeletedAt());
        return new MapSqlParameterSource(values);
    }

    private DownloadLink mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return DownloadLink.builder()
                .id(resultSet.getObject("id", UUID.class))
                .jobId(resultSet.getObject("job_id", UUID.class))
                .fileId(resultSet.getObject("file_id", UUID.class))
                .token(resultSet.getString("token"))
                .chatId(resultSet.getLong("chat_id"))
                .originalFileName(resultSet.getString("original_file_name"))
                .storedFileName(resultSet.getString("stored_file_name"))
                .filePath(resultSet.getString("file_path"))
                .fileSizeBytes(resultSet.getLong("file_size_bytes"))
                .status(DownloadLinkStatus.valueOf(resultSet.getString("status")))
                .expiresAt(resultSet.getTimestamp("expires_at").toLocalDateTime())
                .createdAt(resultSet.getTimestamp("created_at").toLocalDateTime())
                .usedAt(toLocalDateTime(resultSet.getTimestamp("used_at")))
                .deletedAt(toLocalDateTime(resultSet.getTimestamp("deleted_at")))
                .build();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
