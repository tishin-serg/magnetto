package ru.xataaa.torrentbot.telegram;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.job.DownloadJob;
import ru.xataaa.torrentbot.job.DownloadJobRepository;
import ru.xataaa.torrentbot.job.DownloadJobStatus;
import ru.xataaa.torrentbot.job.DownloadTarget;

@Component
@RequiredArgsConstructor
public class TaskOverviewService {

    private static final int MAX_JOBS_IN_OVERVIEW = 10;
    private static final int MAX_TEXT_LENGTH = 3800;

    private final DownloadJobRepository downloadJobRepository;
    private final TimeProvider timeProvider;
    private final ru.xataaa.torrentbot.job.DownloadTelemetry downloadTelemetry;

    public String text() {
        List<DownloadJob> jobs = downloadJobRepository.findRecent(MAX_JOBS_IN_OVERVIEW);
        if (jobs.isEmpty()) {
            return "📥 Загрузки\n\nЗагрузок пока нет. Найди фильм или отправь magnet-ссылку — здесь появится ход скачивания.";
        }

        StringBuilder text = new StringBuilder();
        text.append("📥 Загрузки\n\n");
        for (int index = 0; index < jobs.size(); index++) {
            DownloadJob job = jobs.get(index);
            text.append(index + 1)
                    .append(". ")
                    .append(shortName(job))
                    .append("\n")
                    .append("Статус: ")
                    .append(statusLabel(job))
                    .append("\n")
                    .append("Куда: ")
                    .append(targetLabel(job.getDownloadTarget()))
                    .append("\n");
            text.append(statistics(job));
            if (job.getNextRetryAt() != null && job.getStatus() == DownloadJobStatus.RETRY_WAITING) {
                text.append("Следующая попытка: ")
                        .append(timeProvider.formatTime(job.getNextRetryAt()))
                        .append("\n");
            }
            text.append("ID: ").append(shortJobId(job.getId())).append("\n\n");
        }
        return truncate(text.toString());
    }

    public String keyboard() {
        List<DownloadJob> jobs = downloadJobRepository.findRecent(MAX_JOBS_IN_OVERVIEW);
        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\"inline_keyboard\":[");
        boolean hasRow = false;
        for (DownloadJob job : jobs) {
            if (job.getStatus() == DownloadJobStatus.WAITING_FILE_SELECTION) {
                if (hasRow) keyboard.append(",");
                keyboard.append("[{\"text\":\"Выбрать файлы · ").append(escapeJson(shortName(job)))
                        .append("\",\"callback_data\":\"file:select:page:").append(job.getId()).append(":0\"}]");
                hasRow = true;
                continue;
            }
            if (!isControllable(job.getStatus())) {
                continue;
            }
            if (hasRow) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(escapeJson(actionText(job)))
                    .append("\",\"callback_data\":\"")
                    .append(actionCallback(job))
                    .append("\"}]");
            hasRow = true;
        }
        if (hasRow) {
            keyboard.append(",");
        }
        keyboard.append("[{\"text\":\"Обновить\",\"callback_data\":\"task:list\"}],");
        keyboard.append("[{\"text\":\"🔎 Поиск\",\"callback_data\":\"menu:search\"}],");
        keyboard.append("[{\"text\":\"🏠 Главное меню\",\"callback_data\":\"menu:home\"}]]}");
        return keyboard.toString();
    }

    private String statistics(DownloadJob job) {
        var snapshot = downloadTelemetry.get(job.getId());
        boolean active = job.getStatus() == DownloadJobStatus.DOWNLOADING;
        if (snapshot == null) return active ? "Статистика пока недоступна · обнови чуть позже\n" : "";
        var sizes = new ru.xataaa.torrentbot.common.FileSizeFormatter();
        String result = snapshot.size() > 0 ? "Выбрано: " + sizes.format(snapshot.size()) + "\n" : "";
        if (!active) return result;
        int percent = (int) Math.floor(snapshot.progress() * 100);
        result = "Прогресс: " + percent + "%\n";
        if (snapshot.size() > 0) result += "Скачано ≈ " + sizes.format((long) (snapshot.size() * snapshot.progress()))
                + " из " + sizes.format(snapshot.size()) + "\n";
        if (snapshot.observedAt().isBefore(java.time.Instant.now().minusSeconds(60))) {
            return result + "Данные устарели · загрузчик не обновил статистику\n";
        }
        result += "Скорость: " + sizes.format(snapshot.speed()) + "/с\n";
        result += "Осталось: " + (snapshot.eta() > 0 && snapshot.eta() < 8640000 && snapshot.speed() > 0
                ? Math.max(1, snapshot.eta() / 60) + " мин" : "пока неизвестно") + "\n";
        result += switch (snapshot.state() == null ? "" : snapshot.state()) {
            case "stalledDL" -> "Ожидание источников\n";
            case "stalledUP", "uploading" -> "Загрузчик не скачивает · проверь выбранные файлы\n";
            case "pausedDL", "stoppedDL" -> "Остановлено в загрузчике\n";
            case "queuedDL" -> "В очереди загрузчика\n";
            case "checkingDL", "checkingUP", "checkingResumeData" -> "Проверяются файлы\n";
            case "error", "missingFiles" -> "Ошибка загрузчика · проверь файлы и свободное место\n";
            default -> "";
        };
        return result;
    }

    private boolean isControllable(DownloadJobStatus status) {
        return status == DownloadJobStatus.PAUSED_BY_USER
                || status == DownloadJobStatus.WAITING_METADATA
                || status == DownloadJobStatus.DOWNLOADING
                || status == DownloadJobStatus.RETRY_WAITING;
    }

    private String actionText(DownloadJob job) {
        if (job.getStatus() == DownloadJobStatus.PAUSED_BY_USER) {
            return "Продолжить · " + shortName(job);
        }
        return "Пауза · " + shortName(job);
    }

    private String actionCallback(DownloadJob job) {
        if (job.getStatus() == DownloadJobStatus.PAUSED_BY_USER) {
            return "task:resume:" + job.getId();
        }
        return "task:pause:" + job.getId();
    }

    private String shortName(DownloadJob job) {
        String torrentName = job.getTorrentName();
        if (torrentName == null || torrentName.isBlank()) {
            return "без названия";
        }
        if (torrentName.length() <= 42) {
            return torrentName;
        }
        return torrentName.substring(0, 39) + "...";
    }

    private String shortJobId(UUID jobId) {
        return jobId.toString().substring(0, 8);
    }

    private String statusLabel(DownloadJob job) {
        return switch (job.getStatus()) {
            case QUEUED -> "В очереди";
            case CREATED, ADDING_TO_QBITTORRENT, ADDED_TO_QBITTORRENT -> "Подготовка загрузки";
            case WAITING_METADATA -> "Получаю список файлов";
            case WAITING_SIZE_CONFIRMATION -> "Нужно подтвердить размер в сообщении загрузки";
            case WAITING_FILE_SELECTION -> "Нужно выбрать файлы в сообщении загрузки";
            case DOWNLOADING -> "Скачивается";
            case PAUSED_BY_USER -> "На паузе";
            case DOWNLOAD_COMPLETED, DISCOVERING_FILES, DELIVERY_PENDING -> "Готовлю файлы к отправке";
            case UPLOADING_TO_TELEGRAM -> "Отправляю в Telegram";
            case UPLOADING_TO_S3 -> "Отправляю в S3";
            case S3_UPLOADED, DELIVERY_COMPLETED, CLEANUP_PENDING, CLEANING_UP, CLEANUP_COMPLETED -> "Файлы доставлены · завершаю задачу";
            case FINISHED -> "Готово · файл в медиатеке или чате";
            case RETRY_WAITING, FAILED_RECOVERABLE -> "Временный сбой · повторю автоматически";
            case FAILED_FINAL -> "Не удалось завершить · попробуй создать загрузку заново";
        };
    }

    private String targetLabel(DownloadTarget downloadTarget) {
        DownloadTarget effectiveTarget = downloadTarget == null ? DownloadTarget.VPS : downloadTarget;
        return switch (effectiveTarget) {
            case HOME_PC -> "домашний ПК";
            case S3, S3_LATER -> "S3";
            case VPS -> "VPS";
        };
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String truncate(String value) {
        if (value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH - 40) + "\n\nСписок обрезан. Нажми «Обновить» позже.";
    }
}
