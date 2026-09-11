package ru.xataaa.torrentbot.speed;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DownloadSpeedMonitorRepository {
    private final JdbcTemplate jdbc;

    public void start(UUID jobId, LocalDateTime startedAt) {
        jdbc.update("""
                insert into download_speed_monitor(job_id,started_at,ends_at,next_check_at)
                values (?,?,?,?) on conflict(job_id) do nothing
                """, jobId, startedAt, startedAt.plusMinutes(15), startedAt.plusMinutes(1));
    }

    public List<DownloadSpeedMonitor> findDue(LocalDateTime now, int limit) {
        return jdbc.query("""
                select * from download_speed_monitor
                where stopped_at is null and next_check_at <= ?
                order by next_check_at limit ?
                """, this::map, now, limit);
    }

    public Optional<DownloadSpeedMonitor> find(UUID jobId) {
        return jdbc.query("select * from download_speed_monitor where job_id=?", this::map, jobId)
                .stream().findFirst();
    }

    public void recordMeasurement(UUID jobId, long speed, boolean belowThreshold, LocalDateTime now) {
        jdbc.update("""
                update download_speed_monitor set last_speed_bps=?,
                    consecutive_low_checks=case when ? then consecutive_low_checks+1 else 0 end,
                    next_check_at=?, stopped_at=case when ends_at <= ? then ? else stopped_at end
                where job_id=? and stopped_at is null
                """, speed, belowThreshold, now.plusMinutes(1), now, now, jobId);
    }

    public void recordMeasurementError(UUID jobId, LocalDateTime now) {
        jdbc.update("""
                update download_speed_monitor set consecutive_low_checks=0, next_check_at=?,
                    stopped_at=case when ends_at <= ? then ? else stopped_at end
                where job_id=? and stopped_at is null
                """, now.plusMinutes(1), now, now, jobId);
    }

    public boolean markAlertSent(UUID jobId) {
        return jdbc.update("update download_speed_monitor set alert_sent=true where job_id=? and alert_sent=false and stopped_at is null", jobId) == 1;
    }

    public boolean claimReplacement(UUID jobId, UUID replacementJobId, LocalDateTime now) {
        return jdbc.update("""
                update download_speed_monitor set replacement_job_id=?, decision_resolved=true, stopped_at=coalesce(stopped_at,?)
                where job_id=? and replacement_job_id is null and alert_sent=true and decision_resolved=false
                """, replacementJobId, now, jobId) == 1;
    }

    public boolean keepCurrent(UUID jobId, LocalDateTime now) {
        return jdbc.update("""
                update download_speed_monitor set decision_resolved=true, stopped_at=coalesce(stopped_at,?)
                where job_id=? and alert_sent=true and decision_resolved=false
                """, now, jobId) == 1;
    }

    public void stop(UUID jobId, LocalDateTime now) {
        jdbc.update("update download_speed_monitor set stopped_at=coalesce(stopped_at,?) where job_id=?", now, jobId);
    }

    private DownloadSpeedMonitor map(ResultSet rs, int row) throws SQLException {
        return new DownloadSpeedMonitor(rs.getObject("job_id", UUID.class),
                rs.getTimestamp("started_at").toLocalDateTime(), rs.getTimestamp("ends_at").toLocalDateTime(),
                rs.getTimestamp("next_check_at").toLocalDateTime(), nullableLong(rs, "last_speed_bps"),
                rs.getInt("consecutive_low_checks"), rs.getBoolean("alert_sent"), rs.getBoolean("decision_resolved"),
                nullableTime(rs, "stopped_at"), rs.getObject("replacement_job_id", UUID.class));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime nullableTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
