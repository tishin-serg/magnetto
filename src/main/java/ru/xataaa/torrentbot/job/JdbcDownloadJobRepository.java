package ru.xataaa.torrentbot.job;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.xataaa.torrentbot.common.ErrorCode;

@Repository
@RequiredArgsConstructor
public class JdbcDownloadJobRepository implements DownloadJobRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void save(DownloadJob downloadJob) {
        String sql = """
                insert into download_job (
                    id, chat_id, magnet_url, magnet_url_hash, torrent_hash, torrent_name, status, resume_status,
                    download_target, target_status, target_error_message,
                    error_code, error_message, retry_count, next_retry_at, delete_after_upload,
                    last_reported_progress_percent, status_message_id, created_at, updated_at, completed_at, failed_at
                ) values (
                    :id, :chatId, :magnetUrl, :magnetUrlHash, :torrentHash, :torrentName, :status, :resumeStatus,
                    :downloadTarget, :targetStatus, :targetErrorMessage,
                    :errorCode, :errorMessage, :retryCount, :nextRetryAt, :deleteAfterUpload,
                    :lastReportedProgressPercent, :statusMessageId, :createdAt, :updatedAt, :completedAt, :failedAt
                )
                """;
        jdbcTemplate.update(sql, toParameters(downloadJob));
    }

    @Override
    public Optional<DownloadJob> findById(UUID id) {
        List<DownloadJob> jobs = jdbcTemplate.query(
                "select * from download_job where id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow
        );
        return jobs.stream().findFirst();
    }

    @Override
    public List<DownloadJob> findByStatuses(Set<DownloadJobStatus> statuses, int limit) {
        return jdbcTemplate.query(
                "select * from download_job where status in (:statuses) order by updated_at asc limit :limit",
                new MapSqlParameterSource()
                        .addValue("statuses", statuses.stream().map(Enum::name).toList())
                        .addValue("limit", limit),
                this::mapRow
        );
    }

    @Override
    public List<DownloadJob> findRetryable(LocalDateTime now, int limit) {
        return jdbcTemplate.query(
                """
                select * from download_job
                where status in ('RETRY_WAITING', 'FAILED_RECOVERABLE')
                  and (next_retry_at is null or next_retry_at <= :now)
                order by coalesce(next_retry_at, updated_at) asc
                limit :limit
                """,
                new MapSqlParameterSource().addValue("now", now).addValue("limit", limit),
                this::mapRow
        );
    }

    @Override
    public List<DownloadJob> findRecent(int limit) {
        return jdbcTemplate.query(
                """
                select *
                from download_job
                order by created_at desc
                limit :limit
                """,
                new MapSqlParameterSource("limit", limit),
                this::mapRow
        );
    }

    @Override
    public List<DownloadJob> findQueued(int limit) {
        return jdbcTemplate.query(
                """
                select *
                from download_job
                where status = 'QUEUED'
                order by created_at asc
                limit :limit
                """,
                new MapSqlParameterSource("limit", limit),
                this::mapRow
        );
    }

    @Override
    public boolean hasActiveJob() {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from download_job
                where status not in ('QUEUED', 'FINISHED', 'FAILED_FINAL')
                """,
                new MapSqlParameterSource(),
                Integer.class
        );
        return count != null && count > 0;
    }

    @Override
    public Optional<DownloadJob> findNextQueued() {
        List<DownloadJob> jobs = jdbcTemplate.query(
                """
                select *
                from download_job
                where status = 'QUEUED'
                order by created_at asc
                limit 1
                """,
                new MapSqlParameterSource(),
                this::mapRow
        );
        return jobs.stream().findFirst();
    }

    @Override
    public void updateStatus(UUID jobId, DownloadJobStatus status) {
        jdbcTemplate.update(
                "update download_job set status = :status, resume_status = null, error_code = null, error_message = null, updated_at = :now where id = :id",
                new MapSqlParameterSource().addValue("id", jobId).addValue("status", status.name()).addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void updateStatusWithError(UUID jobId, DownloadJobStatus status, ErrorCode errorCode, String errorMessage) {
        jdbcTemplate.update(
                """
                update download_job
                set status = :status, resume_status = null, error_code = :errorCode, error_message = :errorMessage, updated_at = :now
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", jobId)
                        .addValue("status", status.name())
                        .addValue("errorCode", errorCode == null ? null : errorCode.name())
                        .addValue("errorMessage", errorMessage)
                        .addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void updateTargetStatus(UUID jobId, TargetStatus targetStatus, String targetErrorMessage) {
        jdbcTemplate.update(
                """
                update download_job
                set target_status = :targetStatus, target_error_message = :targetErrorMessage, updated_at = :now
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", jobId)
                        .addValue("targetStatus", targetStatus.name())
                        .addValue("targetErrorMessage", targetErrorMessage)
                        .addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void pauseWithResumeStatus(UUID jobId, DownloadJobStatus resumeStatus) {
        jdbcTemplate.update(
                """
                update download_job
                set status = 'PAUSED_BY_USER', resume_status = :resumeStatus, updated_at = :now
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", jobId)
                        .addValue("resumeStatus", resumeStatus.name())
                        .addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void scheduleRetry(UUID jobId, DownloadJobStatus status, ErrorCode errorCode, String errorMessage, int retryCount, LocalDateTime nextRetryAt) {
        jdbcTemplate.update(
                """
                update download_job
                set status = 'RETRY_WAITING', resume_status = :resumeStatus, error_code = :errorCode, error_message = :errorMessage,
                    retry_count = :retryCount, next_retry_at = :nextRetryAt, updated_at = :now
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", jobId)
                        .addValue("resumeStatus", status.name())
                        .addValue("errorCode", errorCode == null ? null : errorCode.name())
                        .addValue("errorMessage", errorMessage)
                        .addValue("retryCount", retryCount)
                        .addValue("nextRetryAt", nextRetryAt)
                        .addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void updateTorrentIdentity(UUID jobId, String torrentHash, String torrentName) {
        jdbcTemplate.update(
                "update download_job set torrent_hash = :torrentHash, torrent_name = :torrentName, updated_at = :now where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", jobId)
                        .addValue("torrentHash", torrentHash)
                        .addValue("torrentName", torrentName)
                        .addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void updateLastReportedProgress(UUID jobId, int progressPercent) {
        jdbcTemplate.update(
                "update download_job set last_reported_progress_percent = :progress, updated_at = :now where id = :id",
                new MapSqlParameterSource().addValue("id", jobId).addValue("progress", progressPercent).addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void updateStatusMessageId(UUID jobId, Long messageId) {
        jdbcTemplate.update(
                "update download_job set status_message_id = :messageId, updated_at = :now where id = :id",
                new MapSqlParameterSource().addValue("id", jobId).addValue("messageId", messageId).addValue("now", LocalDateTime.now())
        );
    }

    @Override
    public void markCompleted(UUID jobId, LocalDateTime completedAt) {
        jdbcTemplate.update(
                "update download_job set status = 'FINISHED', completed_at = :completedAt, updated_at = :completedAt where id = :id",
                new MapSqlParameterSource().addValue("id", jobId).addValue("completedAt", completedAt)
        );
    }

    @Override
    public void markFailed(UUID jobId, ErrorCode errorCode, String errorMessage, LocalDateTime failedAt) {
        jdbcTemplate.update(
                """
                update download_job
                set status = 'FAILED_FINAL', error_code = :errorCode, error_message = :errorMessage,
                    failed_at = :failedAt, updated_at = :failedAt
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", jobId)
                        .addValue("errorCode", errorCode.name())
                        .addValue("errorMessage", errorMessage)
                        .addValue("failedAt", failedAt)
        );
    }

    private MapSqlParameterSource toParameters(DownloadJob downloadJob) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", downloadJob.getId());
        values.put("chatId", downloadJob.getChatId());
        values.put("magnetUrl", downloadJob.getMagnetUrl());
        values.put("magnetUrlHash", downloadJob.getMagnetUrlHash());
        values.put("torrentHash", downloadJob.getTorrentHash());
        values.put("torrentName", downloadJob.getTorrentName());
        values.put("status", downloadJob.getStatus().name());
        values.put("resumeStatus", downloadJob.getResumeStatus() == null ? null : downloadJob.getResumeStatus().name());
        values.put("downloadTarget", downloadJob.getDownloadTarget() == null ? DownloadTarget.VPS.name() : downloadJob.getDownloadTarget().name());
        values.put("targetStatus", downloadJob.getTargetStatus() == null ? TargetStatus.READY.name() : downloadJob.getTargetStatus().name());
        values.put("targetErrorMessage", downloadJob.getTargetErrorMessage());
        values.put("errorCode", downloadJob.getErrorCode() == null ? null : downloadJob.getErrorCode().name());
        values.put("errorMessage", downloadJob.getErrorMessage());
        values.put("retryCount", downloadJob.getRetryCount());
        values.put("nextRetryAt", downloadJob.getNextRetryAt());
        values.put("deleteAfterUpload", downloadJob.isDeleteAfterUpload());
        values.put("lastReportedProgressPercent", downloadJob.getLastReportedProgressPercent());
        values.put("statusMessageId", downloadJob.getStatusMessageId());
        values.put("createdAt", downloadJob.getCreatedAt());
        values.put("updatedAt", downloadJob.getUpdatedAt());
        values.put("completedAt", downloadJob.getCompletedAt());
        values.put("failedAt", downloadJob.getFailedAt());
        return new MapSqlParameterSource(values);
    }

    private DownloadJob mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        String errorCodeValue = resultSet.getString("error_code");
        String resumeStatusValue = resultSet.getString("resume_status");
        String downloadTargetValue = resultSet.getString("download_target");
        String targetStatusValue = resultSet.getString("target_status");
        Long statusMessageId = resultSet.getLong("status_message_id");
        if (resultSet.wasNull()) {
            statusMessageId = null;
        }
        return DownloadJob.builder()
                .id(resultSet.getObject("id", UUID.class))
                .chatId(resultSet.getLong("chat_id"))
                .magnetUrl(resultSet.getString("magnet_url"))
                .magnetUrlHash(resultSet.getString("magnet_url_hash"))
                .torrentHash(resultSet.getString("torrent_hash"))
                .torrentName(resultSet.getString("torrent_name"))
                .status(DownloadJobStatus.valueOf(resultSet.getString("status")))
                .resumeStatus(resumeStatusValue == null ? null : DownloadJobStatus.valueOf(resumeStatusValue))
                .downloadTarget(DownloadTarget.fromValue(downloadTargetValue))
                .targetStatus(targetStatusValue == null ? TargetStatus.READY : TargetStatus.valueOf(targetStatusValue))
                .targetErrorMessage(resultSet.getString("target_error_message"))
                .errorCode(errorCodeValue == null ? null : ErrorCode.valueOf(errorCodeValue))
                .errorMessage(resultSet.getString("error_message"))
                .retryCount(resultSet.getInt("retry_count"))
                .nextRetryAt(toLocalDateTime(resultSet.getTimestamp("next_retry_at")))
                .deleteAfterUpload(resultSet.getBoolean("delete_after_upload"))
                .lastReportedProgressPercent(resultSet.getInt("last_reported_progress_percent"))
                .statusMessageId(statusMessageId)
                .createdAt(resultSet.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(resultSet.getTimestamp("updated_at").toLocalDateTime())
                .completedAt(toLocalDateTime(resultSet.getTimestamp("completed_at")))
                .failedAt(toLocalDateTime(resultSet.getTimestamp("failed_at")))
                .build();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
