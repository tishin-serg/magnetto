package ru.xataaa.torrentbot.job;

import java.sql.ResultSet;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

class DownloadSizeGuardTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final DownloadJobRepository repository = mock(DownloadJobRepository.class);
    final QbittorrentTorrentService torrents = mock(QbittorrentTorrentService.class);
    final TelegramMessageService messages = mock(TelegramMessageService.class);
    final DownloadSizeGuard guard = new DownloadSizeGuard(jdbc, repository, torrents, messages);
    final UUID id = UUID.randomUUID();
    final DownloadJob job = DownloadJob.builder().id(id).chatId(42L).downloadTarget(DownloadTarget.HOME_PC)
            .status(DownloadJobStatus.WAITING_SIZE_CONFIRMATION).build();

    @BeforeEach void policy() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("min_bytes")).thenReturn(4_000_000_000L);
        when(rs.getLong("max_bytes")).thenReturn(15_000_000_000L);
        when(jdbc.query(eq("select * from download_size_policy where job_id=?"), any(RowMapper.class), eq(id)))
                .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(1)).mapRow(rs, 0)));
    }

    @Test void outOfRangePausesAndRequiresFreshConfirmation() {
        assertThat(guard.check(job, "hash", 16_000_000_000L)).isFalse();
        verify(torrents).pauseTorrent(DownloadTarget.HOME_PC, "hash");
        verify(torrents, never()).resumeTorrent(any(), anyString());
        verify(repository).updateStatus(id, DownloadJobStatus.WAITING_SIZE_CONFIRMATION);
        verify(messages).sendTextWithInlineKeyboard(eq(42L), contains("16 ГБ"), contains("size:approve:"));
    }

    @Test void exactBoundaryPassesAndStillPausesBeforeFileSelection() {
        assertThat(guard.check(job, "hash", 15_000_000_000L)).isTrue();
        verify(repository, never()).updateStatus(any(), any());
        verify(torrents).pauseTorrent(DownloadTarget.HOME_PC, "hash");
    }

    @Test void anotherUserCannotApproveSize() {
        when(repository.findById(id)).thenReturn(Optional.of(job));
        guard.handle("q", 43L, 100L, "size:approve:" + id);
        verify(jdbc, never()).update(anyString(), any(UUID.class));
        verify(repository, never()).updateStatus(any(), any());
    }
}