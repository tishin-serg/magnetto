package ru.xataaa.torrentbot.job;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.preferences.DownloadPreferences;
import ru.xataaa.torrentbot.qbittorrent.QbittorrentTorrentService;
import ru.xataaa.torrentbot.telegram.TelegramCallbackHandler;
import ru.xataaa.torrentbot.telegram.TelegramMessageService;

@Component
@RequiredArgsConstructor
public class DownloadSizeGuard implements TelegramCallbackHandler {
    private final JdbcTemplate jdbc;
    private final DownloadJobRepository repository;
    private final QbittorrentTorrentService torrents;
    private final TelegramMessageService messages;

    @org.springframework.transaction.annotation.Transactional
    public void saveJob(DownloadJob job, DownloadPreferences preferences) {
        repository.save(job);
        save(job.getId(), preferences);
    }

    public void save(UUID jobId, DownloadPreferences preferences) {
        jdbc.update("insert into download_size_policy(job_id,min_bytes,max_bytes) values (?,?,?)",
                jobId, preferences.minBytes(), preferences.maxBytes());
    }

    public boolean applies(UUID jobId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from download_size_policy where job_id=?)", Boolean.class, jobId));
    }

    public boolean check(DownloadJob job, String hash, long actualBytes) {
        var policies = jdbc.query("select * from download_size_policy where job_id=?", (rs, row) ->
                new Policy(rs.getLong("min_bytes"), rs.getLong("max_bytes"), rs.getBoolean("approved")), job.getId());
        if (policies.isEmpty()) return true;
        torrents.pauseTorrent(job.getDownloadTarget(), hash);
        Policy policy = policies.getFirst();
        if (actualBytes > 0 && (policy.approved || (actualBytes >= policy.min && actualBytes <= policy.max))) return true;
        // Keep the torrent paused until a separate, explicit confirmation.
        String text = "Размер по метаданным: " + DownloadPreferences.gb(actualBytes) + " ГБ."
                + "\nУсловия заявки: от " + DownloadPreferences.gb(policy.min) + " до " + DownloadPreferences.gb(policy.max)
                + " ГБ.\nЗагрузка приостановлена. Разрешить этот размер?";
        String keyboard = """
                {"inline_keyboard":[[{"text":"Скачать с этим размером","callback_data":"size:approve:%s"}],
                [{"text":"Отменить загрузку","callback_data":"size:cancel:%s"}]]}
                """.formatted(job.getId(), job.getId());
        messages.sendTextWithInlineKeyboard(job.getChatId(), text, keyboard);
        repository.updateStatus(job.getId(), DownloadJobStatus.WAITING_SIZE_CONFIRMATION);
        return false;
    }

    public void resumeValidated(DownloadJob job, String hash) {
        if (applies(job.getId())) torrents.resumeTorrent(job.getDownloadTarget(), hash);
    }

    @Override public boolean supports(String data) { return data != null && data.startsWith("size:"); }

    @Override public synchronized void handle(String queryId, Long chatId, Long messageId, String data) {
        String[] parts = data.split(":");
        UUID jobId;
        try { jobId = UUID.fromString(parts[2]); }
        catch (RuntimeException exception) { messages.answerCallbackQuery(queryId, "Некорректная кнопка"); return; }
        var job = repository.findById(jobId).orElse(null);
        if (job == null || !job.getChatId().equals(chatId) || job.getStatus() != DownloadJobStatus.WAITING_SIZE_CONFIRMATION) {
            messages.answerCallbackQuery(queryId, "Подтверждение устарело"); return;
        }
        if ("approve".equals(parts[1])) {
            jdbc.update("update download_size_policy set approved=true where job_id=?", jobId);
            repository.updateStatus(jobId, DownloadJobStatus.WAITING_METADATA);
            messages.answerCallbackQuery(queryId, "Размер подтверждён");
            messages.editText(chatId, messageId, "Размер подтверждён. Продолжаю обработку загрузки.", "{\"inline_keyboard\":[]}");
        } else if ("cancel".equals(parts[1])) {
            repository.updateStatus(jobId, DownloadJobStatus.FAILED_FINAL);
            messages.answerCallbackQuery(queryId, "Отменено");
            messages.editText(chatId, messageId, "Загрузка отменена. Torrent оставлен на паузе.", "{\"inline_keyboard\":[]}");
        }
    }

    private record Policy(long min, long max, boolean approved) {}
}