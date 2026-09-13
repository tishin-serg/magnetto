package ru.xataaa.torrentbot.application;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TemporaryDeliveryArtifactRepository {
    private final JdbcTemplate jdbc;
    public void save(UUID jobId, UUID fileId, String storageType, String key, LocalDateTime expiresAt) {
        jdbc.update("insert into temporary_delivery_artifact(id,job_id,file_id,storage_type,token_or_s3_key,expires_at,cleanup_status) values(?,?,?,?,?,?,?)", UUID.randomUUID(), jobId, fileId, storageType, key, expiresAt, "PENDING");
    }
    public java.util.List<Artifact> findExpired(LocalDateTime now) {
        return jdbc.query("select id,job_id,file_id,storage_type,token_or_s3_key from temporary_delivery_artifact where cleanup_status='PENDING' and expires_at<=?", (rs,n) -> new Artifact(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5)), now);
    }
    public void markCleaned(UUID id, LocalDateTime now) { jdbc.update("update temporary_delivery_artifact set cleanup_status='CLEANED',cleaned_at=? where id=?", now, id); }
    public record Artifact(UUID id, UUID jobId, UUID fileId, String storageType, String key) {}
}
