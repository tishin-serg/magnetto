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

    public String text() {
        List<DownloadJob> jobs = downloadJobRepository.findRecent(MAX_JOBS_IN_OVERVIEW);
        if (jobs.isEmpty()) {
            return "Активных и завершённых задач пока нет.";
        }

        StringBuilder text = new StringBuilder();
        text.append("Задачи\n\n");
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
            if (job.getNextRetryAt() != null && job.getStatus() == DownloadJobStatus.RETRY_WAITING) {
                text.append("Следующая попытка: ")
                        .append(timeProvider.formatTime(job.getNextRetryAt()))
                        .append("\n");
            }
            text.append("ID: ").append(shortJobId(job.getId())).append("\n\n");
        }
        text.append("Кнопками ниже можно поставить задачу на паузу, продолжить её или обновить список.");
        return truncate(text.toString());
    }

    public String keyboard() {
        List<DownloadJob> jobs = downloadJobRepository.findRecent(MAX_JOBS_IN_OVERVIEW);
        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\"inline_keyboard\":[");
        boolean hasRow = false;
        for (DownloadJob job : jobs) {
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
        keyboard.append("[{\"text\":\"Назад в меню\",\"callback_data\":\"menu:home\"}]]}");
        return keyboard.toString();
    }

    private boolean isControllable(DownloadJobStatus status) {
        return status == DownloadJobStatus.PAUSED_BY_USER
                || status == DownloadJobStatus.WAITING_METADATA
                || status == DownloadJobStatus.DOWNLOADING
                || status == DownloadJobStatus.RETRY_WAITING;
    }

    private String actionText(DownloadJob job) {
        if (job.getStatus() == DownloadJobStatus.PAUSED_BY_USER) {
            return "Продолжить " + shortJobId(job.getId()) + " " + shortName(job);
        }
        return "Пауза " + shortJobId(job.getId()) + " " + shortName(job);
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
        if (job.getStatus() == DownloadJobStatus.RETRY_WAITING && job.getResumeStatus() != null) {
            return "ожидает повторной попытки (" + job.getResumeStatus().name() + ")";
        }
        return job.getStatus().name();
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
