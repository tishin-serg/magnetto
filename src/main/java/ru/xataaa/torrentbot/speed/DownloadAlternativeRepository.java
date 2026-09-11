package ru.xataaa.torrentbot.speed;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchResult;

@Repository
@RequiredArgsConstructor
public class DownloadAlternativeRepository {
    private final JdbcTemplate jdbc;

    public void saveResults(UUID jobId, String currentMagnet, List<TorrentSearchResult> candidates) {
        if (candidates == null) return;
        int position = 0;
        for (TorrentSearchResult candidate : candidates) {
            if (candidate == null || !candidate.hasMagnet() || candidate.magnetUri().equals(currentMagnet)) continue;
            jdbc.update("insert into download_alternative(id,job_id,position,magnet_url,title,size_bytes) values (?,?,?,?,?,?)",
                    UUID.randomUUID(), jobId, position++, candidate.magnetUri(), candidate.title(), candidate.sizeBytes());
        }
    }

    public void saveAlternatives(UUID jobId, List<DownloadAlternative> alternatives) {
        int position = 0;
        for (DownloadAlternative item : alternatives) {
            jdbc.update("insert into download_alternative(id,job_id,position,magnet_url,title,size_bytes) values (?,?,?,?,?,?)",
                    UUID.randomUUID(), jobId, position++, item.magnetUrl(), item.title(), item.sizeBytes());
        }
    }

    public List<DownloadAlternative> findByJobId(UUID jobId) {
        return jdbc.query("select position,magnet_url,title,size_bytes from download_alternative where job_id=? order by position",
                (rs, row) -> new DownloadAlternative(rs.getInt("position"), rs.getString("magnet_url"),
                        rs.getString("title"), rs.getLong("size_bytes")), jobId);
    }

}
