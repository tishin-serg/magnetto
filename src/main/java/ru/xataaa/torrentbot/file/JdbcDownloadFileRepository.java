package ru.xataaa.torrentbot.file;

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
import ru.xataaa.torrentbot.common.ErrorCode;

@Repository
@RequiredArgsConstructor
public class JdbcDownloadFileRepository implements DownloadFileRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void save(DownloadFile downloadFile) {
        String sql = """
                insert into download_file (
                    id, job_id, file_name, relative_path, torrent_file_index, size_bytes, status, telegram_message_id,
                    s3_object_key, upload_attempts, cleanup_attempts, error_code, error_message, created_at, updated_at
                ) values (
                    :id, :jobId, :fileName, :relativePath, :torrentFileIndex, :sizeBytes, :status, :telegramMessageId,
                    :s3ObjectKey, :uploadAttempts, :cleanupAttempts, :errorCode, :errorMessage, :createdAt, :updatedAt
                )
                """;
        jdbcTemplate.update(sql, toParameters(downloadFile));
    }

    @Override
    public void saveIfAbsent(DownloadFile downloadFile) {
        String sql = """
                insert into download_file (
                    id, job_id, file_name, relative_path, torrent_file_index, size_bytes, status, telegram_message_id,
                    s3_object_key, upload_attempts, cleanup_attempts, error_code, error_message, created_at, updated_at
                ) values (
                    :id, :jobId, :fileName, :relativePath, :torrentFileIndex, :sizeBytes, :status, :telegramMessageId,
                    :s3ObjectKey, :uploadAttempts, :cleanupAttempts, :errorCode, :errorMessage, :createdAt, :updatedAt
                )
                on conflict do nothing
                """;
        jdbcTemplate.update(sql, toParameters(downloadFile));
    }

    @Override
    public List<DownloadFile> findByJobId(UUID jobId) {
        return jdbcTemplate.query(
                "select * from download_file where job_id = :jobId order by file_name",
                new MapSqlParameterSource("jobId", jobId),
                this::mapRow
        );
    }

    @Override
    public List<DownloadFile> findByJobIdAndStatuses(UUID jobId, List<DownloadFileStatus> statuses) {
        return jdbcTemplate.query(
                "select * from download_file where job_id = :jobId and status in (:statuses) order by file_name",
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("statuses", statuses.stream().map(Enum::name).toList()),
                this::mapRow
        );
    }

    @Override
    public Optional<DownloadFile> findById(UUID id) {
        List<DownloadFile> files = jdbcTemplate.query(
                "select * from download_file where id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow
        );
        return files.stream().findFirst();
    }

    @Override
    public void updateStatus(UUID fileId, DownloadFileStatus status) {
        jdbcTemplate.update(
                "update download_file set status = :status, updated_at = :now where id = :id",
                new MapSqlParameterSource().addValue("id", fileId).addValue("status", status.name()).addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void updateStatusWithError(UUID fileId, DownloadFileStatus status, ErrorCode errorCode, String errorMessage) {
        jdbcTemplate.update(
                """
                update download_file
                set status = :status, error_code = :errorCode, error_message = :errorMessage, updated_at = :now
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", fileId)
                        .addValue("status", status.name())
                        .addValue("errorCode", errorCode == null ? null : errorCode.name())
                        .addValue("errorMessage", errorMessage)
                        .addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void markUploaded(UUID fileId, Long telegramMessageId) {
        jdbcTemplate.update(
                """
                update download_file
                set status = 'UPLOADED', telegram_message_id = :telegramMessageId,
                    error_code = null, error_message = null, updated_at = :now
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", fileId)
                        .addValue("telegramMessageId", telegramMessageId)
                        .addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void markS3Uploaded(UUID fileId, String s3ObjectKey) {
        jdbcTemplate.update(
                """
                update download_file
                set status = 'S3_UPLOADED', s3_object_key = :s3ObjectKey,
                    error_code = null, error_message = null, updated_at = :now
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", fileId)
                        .addValue("s3ObjectKey", s3ObjectKey)
                        .addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void incrementUploadAttempt(UUID fileId, DownloadFileStatus status, ErrorCode errorCode, String errorMessage) {
        jdbcTemplate.update(
                """
                update download_file
                set status = :status, upload_attempts = upload_attempts + 1,
                    error_code = :errorCode, error_message = :errorMessage, updated_at = :now
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", fileId)
                        .addValue("status", status.name())
                        .addValue("errorCode", errorCode.name())
                        .addValue("errorMessage", errorMessage)
                        .addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void markStaleUploads(UUID jobId, LocalDateTime staleBefore) {
        jdbcTemplate.update(
                """
                update download_file
                set status = 'UNKNOWN_UPLOAD_RESULT',
                    error_code = 'TELEGRAM_UPLOAD_UNKNOWN_RESULT',
                    error_message = 'Upload result is unknown after timeout',
                    updated_at = :now
                where job_id = :jobId and status = 'UPLOADING' and updated_at <= :staleBefore
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("staleBefore", staleBefore)
                        .addValue("now", LocalDateTime.now())
        );
    }

    private MapSqlParameterSource toParameters(DownloadFile downloadFile) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", downloadFile.getId());
        values.put("jobId", downloadFile.getJobId());
        values.put("fileName", downloadFile.getFileName());
        values.put("relativePath", downloadFile.getRelativePath());
        values.put("torrentFileIndex", downloadFile.getTorrentFileIndex());
        values.put("sizeBytes", downloadFile.getSizeBytes());
        values.put("status", downloadFile.getStatus().name());
        values.put("telegramMessageId", downloadFile.getTelegramMessageId());
        values.put("s3ObjectKey", downloadFile.getS3ObjectKey());
        values.put("uploadAttempts", downloadFile.getUploadAttempts());
        values.put("cleanupAttempts", downloadFile.getCleanupAttempts());
        values.put("errorCode", downloadFile.getErrorCode() == null ? null : downloadFile.getErrorCode().name());
        values.put("errorMessage", downloadFile.getErrorMessage());
        values.put("createdAt", downloadFile.getCreatedAt());
        values.put("updatedAt", downloadFile.getUpdatedAt());
        return new MapSqlParameterSource(values);
    }

    private DownloadFile mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        String errorCodeValue = resultSet.getString("error_code");
        Long telegramMessageId = resultSet.getLong("telegram_message_id");
        if (resultSet.wasNull()) {
            telegramMessageId = null;
        }
        return DownloadFile.builder()
                .id(resultSet.getObject("id", UUID.class))
                .jobId(resultSet.getObject("job_id", UUID.class))
                .fileName(resultSet.getString("file_name"))
                .relativePath(resultSet.getString("relative_path"))
                .torrentFileIndex(readNullableInteger(resultSet, "torrent_file_index"))
                .sizeBytes(resultSet.getLong("size_bytes"))
                .status(DownloadFileStatus.valueOf(resultSet.getString("status")))
                .telegramMessageId(telegramMessageId)
                .s3ObjectKey(resultSet.getString("s3_object_key"))
                .uploadAttempts(resultSet.getInt("upload_attempts"))
                .cleanupAttempts(resultSet.getInt("cleanup_attempts"))
                .errorCode(errorCodeValue == null ? null : ErrorCode.valueOf(errorCodeValue))
                .errorMessage(resultSet.getString("error_message"))
                .createdAt(resultSet.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(resultSet.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }

    private Integer readNullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }
}
