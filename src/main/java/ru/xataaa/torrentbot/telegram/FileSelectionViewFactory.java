package ru.xataaa.torrentbot.telegram;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.file.DownloadFile;
import ru.xataaa.torrentbot.file.DownloadFileStatus;

@Component
@RequiredArgsConstructor
public class FileSelectionViewFactory {

    private static final int PAGE_SIZE = 8;
    private static final int TEXT_FILE_NAME_LIMIT = 96;
    private static final int BUTTON_FILE_NAME_LIMIT = 34;

    private final FileSizeFormatter fileSizeFormatter;

    public String text(List<DownloadFile> files, int requestedPage) {
        int page = normalizePage(files, requestedPage);
        int totalPages = totalPages(files);
        int fromIndex = page * PAGE_SIZE;
        int toIndex = Math.min(files.size(), fromIndex + PAGE_SIZE);

        int selectedCount = 0;
        long selectedBytes = 0L;
        for (DownloadFile file : files) {
            if (file.getStatus() == DownloadFileStatus.READY_TO_UPLOAD) {
                selectedCount++;
                selectedBytes += file.getSizeBytes();
            }
        }

        StringBuilder text = new StringBuilder();
        text.append("Что скачать из раздачи?\n\n");
        text.append("Внутри torrent несколько видеофайлов. Отметь только нужные серии или файлы.\n\n");
        text.append("Выбрано: ").append(selectedCount).append(" из ").append(files.size()).append("\n");
        text.append("Общий размер выбранного: ").append(fileSizeFormatter.format(selectedBytes)).append("\n");
        text.append("Страница: ").append(page + 1).append(" из ").append(totalPages).append("\n\n");

        for (int index = fromIndex; index < toIndex; index++) {
            DownloadFile file = files.get(index);
            String mark = file.getStatus() == DownloadFileStatus.READY_TO_UPLOAD ? "[x] " : "[ ] ";
            text.append(index + 1)
                    .append(". ")
                    .append(mark)
                    .append(shorten(file.getFileName(), TEXT_FILE_NAME_LIMIT))
                    .append(" - ")
                    .append(fileSizeFormatter.format(file.getSizeBytes()))
                    .append("\n");
        }

        text.append("\nСкачивание продолжится только после «Скачать выбранные». «Скачать всё» означает весь показанный набор файлов.");
        return text.toString();
    }

    public String keyboard(List<DownloadFile> files, UUID jobId, int requestedPage) {
        int page = normalizePage(files, requestedPage);
        int totalPages = totalPages(files);
        int fromIndex = page * PAGE_SIZE;
        int toIndex = Math.min(files.size(), fromIndex + PAGE_SIZE);

        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\"inline_keyboard\":[");
        keyboard.append("[{\"text\":\"Скачать выбранные\",\"callback_data\":\"file:select:done:")
                .append(jobId)
                .append("\"}],");
        keyboard.append("[{\"text\":\"Скачать всё явно\",\"callback_data\":\"file:select:all:")
                .append(jobId)
                .append("\"}]");

        for (int index = fromIndex; index < toIndex; index++) {
            DownloadFile file = files.get(index);
            String mark = file.getStatus() == DownloadFileStatus.READY_TO_UPLOAD ? "✅ " : "⬜ ";
            keyboard.append(",[{\"text\":\"")
                    .append(escapeJson(mark + shorten(file.getFileName(), BUTTON_FILE_NAME_LIMIT)))
                    .append("\",\"callback_data\":\"file:toggle:")
                    .append(file.getId())
                    .append(":")
                    .append(page)
                    .append("\"}]");
        }

        if (totalPages > 1) {
            keyboard.append(",[");
            boolean hasNavigationButton = false;
            if (page > 0) {
                keyboard.append("{\"text\":\"Назад\",\"callback_data\":\"file:select:page:")
                        .append(jobId)
                        .append(":")
                        .append(page - 1)
                        .append("\"}");
                hasNavigationButton = true;
            }
            if (page + 1 < totalPages) {
                if (hasNavigationButton) {
                    keyboard.append(",");
                }
                keyboard.append("{\"text\":\"Вперёд\",\"callback_data\":\"file:select:page:")
                        .append(jobId)
                        .append(":")
                        .append(page + 1)
                        .append("\"}");
            }
            keyboard.append("]");
        }

        keyboard.append("]}");
        return keyboard.toString();
    }

    public int normalizePage(List<DownloadFile> files, int requestedPage) {
        int totalPages = totalPages(files);
        if (requestedPage < 0) {
            return 0;
        }
        if (requestedPage >= totalPages) {
            return totalPages - 1;
        }
        return requestedPage;
    }

    private int totalPages(List<DownloadFile> files) {
        if (files == null || files.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil((double) files.size() / PAGE_SIZE);
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return "файл";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
