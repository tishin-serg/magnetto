package ru.xataaa.torrentbot.telegram;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.downloadlink.HomeDownloadLinkService;
import ru.xataaa.torrentbot.media.HomeMediaLibraryFile;
import ru.xataaa.torrentbot.media.HomeMediaLibraryItem;
import ru.xataaa.torrentbot.media.S3MediaLibraryFile;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;

@Component
public class TelegramKeyboardFactory {

    public String mainMenuKeyboard() {
        return """
                {"inline_keyboard":[
                  [{"text":"Найти фильм","callback_data":"menu:search"}],
                  [{"text":"Задачи","callback_data":"menu:tasks"}],
                  [{"text":"Домашняя медиатека","callback_data":"menu:library:home"}],
                  [{"text":"VPS медиатека","callback_data":"menu:library:vps"}],
                  [{"text":"S3 медиатека","callback_data":"menu:library:s3"}],
                  [{"text":"Свободное место","callback_data":"menu:space"}],
                  [{"text":"Очистить медиатеку","callback_data":"media:cleanup:ask"}],
                  [{"text":"Инструкция iPhone","callback_data":"menu:iphone"}]
                ]}
                """;
    }

    public String cleanupConfirmKeyboard() {
        return """
                {"inline_keyboard":[
                  [{"text":"Очистить медиатеку VPS","callback_data":"media:cleanup:confirm:local"}],
                  [{"text":"Очистить домашнюю медиатеку","callback_data":"media:cleanup:confirm:home"}],
                  [{"text":"Очистить S3 медиатеку","callback_data":"media:cleanup:confirm:s3"}],
                  [{"text":"Отмена","callback_data":"menu:home"}]
                ]}
                """;
    }

    public String backToMenuKeyboard() {
        return """
                {"inline_keyboard":[
                  [{"text":"Назад в меню","callback_data":"menu:home"}]
                ]}
                """;
    }

    public String searchLauncherKeyboard() {
        return """
                {"inline_keyboard":[
                  [{"text":"Найти через TMDb","switch_inline_query_current_chat":""}],
                  [{"text":"Назад в меню","callback_data":"menu:home"}]
                ]}
                """;
    }

    public String homeMediaLibraryKeyboard(
            String homeWebdavUrl,
            String homeLocalWebdavUrl,
            String vpsWebdavUrl,
            List<HomeMediaLibraryItem> homeItems,
            HomeDownloadLinkService homeDownloadLinkService,
            int page,
            int pageSize
    ) {
        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\"inline_keyboard\":[");
        boolean hasButton = false;
        hasButton = appendHomeItemButtons(keyboard, hasButton, homeItems, homeDownloadLinkService, page, pageSize);
        hasButton = appendHomeLibraryNavigation(keyboard, hasButton, homeItems, page, pageSize);
        hasButton = appendUrlButton(keyboard, hasButton, "Открыть домашнюю WebDAV", homeWebdavUrl);
        hasButton = appendUrlButton(keyboard, hasButton, "Открыть дома по Wi-Fi", homeLocalWebdavUrl);
        hasButton = appendUrlButton(keyboard, hasButton, "Открыть VPS WebDAV", vpsWebdavUrl);
        if (hasButton) {
            keyboard.append(",");
        }
        keyboard.append("[{\"text\":\"Обновить\",\"callback_data\":\"menu:library:home:page:")
                .append(normalizePage(homeItems, page, pageSize))
                .append("\"}],");
        keyboard.append("[{\"text\":\"Назад в меню\",\"callback_data\":\"menu:home\"}]]}");
        return keyboard.toString();
    }

    public String homeFolderKeyboard(
            String folderKey,
            List<HomeMediaLibraryFile> folderFiles,
            HomeDownloadLinkService homeDownloadLinkService,
            int page,
            int pageSize
    ) {
        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\"inline_keyboard\":[");
        boolean hasButton = false;
        hasButton = appendHomeFileButtons(keyboard, hasButton, folderFiles, homeDownloadLinkService, page, pageSize);
        hasButton = appendHomeFolderNavigation(keyboard, hasButton, folderKey, folderFiles, page, pageSize);
        if (hasButton) {
            keyboard.append(",");
        }
        keyboard.append("[{\"text\":\"Обновить\",\"callback_data\":\"menu:library:home:folder:")
                .append(escapeJson(folderKey))
                .append(":page:")
                .append(normalizePage(folderFiles, page, pageSize))
                .append("\"}],");
        keyboard.append("[{\"text\":\"Назад к медиатеке\",\"callback_data\":\"menu:library:home\"}],");
        keyboard.append("[{\"text\":\"Назад в меню\",\"callback_data\":\"menu:home\"}]]}");
        return keyboard.toString();
    }

    public String homeFileKeyboard(String fileKey, String tailscaleUrl, String localWifiUrl) {
        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\"inline_keyboard\":[");
        keyboard.append("[{\"text\":\"Скачать на iPhone\",\"callback_data\":\"home:link:")
                .append(escapeJson(fileKey))
                .append("\"}]");
        boolean hasButton = true;
        hasButton = appendUrlButton(keyboard, hasButton, "Открыть папку через Tailscale", tailscaleUrl);
        hasButton = appendUrlButton(keyboard, hasButton, "Открыть дома по Wi-Fi", localWifiUrl);
        if (hasButton) {
            keyboard.append(",");
        }
        keyboard.append("[{\"text\":\"Удалить файл\",\"callback_data\":\"home:delete:ask:")
                .append(escapeJson(fileKey))
                .append("\"}],");
        keyboard.append("[{\"text\":\"Назад к медиатеке\",\"callback_data\":\"menu:library:home\"}]]}");
        return keyboard.toString();
    }

    public String homeFileDeleteConfirmKeyboard(String fileKey) {
        return "{\"inline_keyboard\":["
                + "[{\"text\":\"Да, удалить файл\",\"callback_data\":\"home:delete:confirm:" + escapeJson(fileKey) + "\"}],"
                + "[{\"text\":\"Отмена\",\"callback_data\":\"home:file:" + escapeJson(fileKey) + "\"}],"
                + "[{\"text\":\"Назад к медиатеке\",\"callback_data\":\"menu:library:home\"}]]}";
    }

    public String homePcFinishedKeyboard() {
        return """
                {"inline_keyboard":[
                  [{"text":"Домашняя медиатека","callback_data":"menu:library:home"}],
                  [{"text":"Инструкция iPhone","callback_data":"menu:iphone"}],
                  [{"text":"Назад в меню","callback_data":"menu:home"}]
                ]}
                """;
    }

    public String s3FileKeyboard(String fileKey) {
        return "{\"inline_keyboard\":["
                + "[{\"text\":\"Скачать\",\"callback_data\":\"s3:download:" + escapeJson(fileKey) + "\"}],"
                + "[{\"text\":\"Удалить файл\",\"callback_data\":\"s3:delete:ask:" + escapeJson(fileKey) + "\"}],"
                + "[{\"text\":\"Назад к S3 медиатеке\",\"callback_data\":\"menu:library:s3\"}]]}";
    }

    public String s3FileDownloadKeyboard(String fileKey, String url) {
        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\"inline_keyboard\":[");
        boolean hasButton = appendUrlButton(keyboard, false, "Открыть ссылку", url);
        if (hasButton) {
            keyboard.append(",");
        }
        keyboard.append("[{\"text\":\"Назад к файлу\",\"callback_data\":\"s3:file:")
                .append(escapeJson(fileKey))
                .append("\"}],");
        keyboard.append("[{\"text\":\"Назад к S3 медиатеке\",\"callback_data\":\"menu:library:s3\"}]]}");
        return keyboard.toString();
    }

    public String s3FileDeleteConfirmKeyboard(String fileKey) {
        return "{\"inline_keyboard\":["
                + "[{\"text\":\"Да, удалить файл\",\"callback_data\":\"s3:delete:confirm:" + escapeJson(fileKey) + "\"}],"
                + "[{\"text\":\"Отмена\",\"callback_data\":\"s3:file:" + escapeJson(fileKey) + "\"}],"
                + "[{\"text\":\"Назад к S3 медиатеке\",\"callback_data\":\"menu:library:s3\"}]]}";
    }

    public String s3MediaLibraryKeyboard(List<S3MediaLibraryFile> files, S3MediaLibraryService s3MediaLibraryService, int page, int pageSize) {
        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\"inline_keyboard\":[");
        boolean hasButton = appendS3FileButtons(keyboard, false, files, s3MediaLibraryService, page, pageSize);
        hasButton = appendS3LibraryNavigation(keyboard, hasButton, files, page, pageSize);
        if (hasButton) {
            keyboard.append(",");
        }
        keyboard.append("[{\"text\":\"Обновить\",\"callback_data\":\"menu:library:s3:page:")
                .append(normalizePage(files, page, pageSize))
                .append("\"}],");
        keyboard.append("[{\"text\":\"Назад в меню\",\"callback_data\":\"menu:home\"}]]}");
        return keyboard.toString();
    }

    public String singleUrlKeyboard(String text, String url) {
        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\"inline_keyboard\":[");
        boolean hasButton = appendUrlButton(keyboard, false, text, url);
        if (hasButton) {
            keyboard.append(",");
        }
        keyboard.append("[{\"text\":\"Домашняя медиатека\",\"callback_data\":\"menu:library:home\"}]]}");
        return keyboard.toString();
    }

    public String vpsLibraryKeyboard(String url) {
        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\"inline_keyboard\":[");
        boolean hasButton = appendUrlButton(keyboard, false, "Открыть VPS WebDAV", url);
        if (hasButton) {
            keyboard.append(",");
        }
        keyboard.append("[{\"text\":\"Назад в меню\",\"callback_data\":\"menu:home\"}]]}");
        return keyboard.toString();
    }

    private boolean appendHomeItemButtons(
            StringBuilder keyboard,
            boolean hasButton,
            List<HomeMediaLibraryItem> homeItems,
            HomeDownloadLinkService homeDownloadLinkService,
            int page,
            int pageSize
    ) {
        if (homeItems == null || homeItems.isEmpty()) {
            return hasButton;
        }
        boolean appended = hasButton;
        int normalizedPage = normalizePage(homeItems, page, pageSize);
        int fromIndex = normalizedPage * pageSize;
        int toIndex = Math.min(homeItems.size(), fromIndex + pageSize);
        for (int itemIndex = fromIndex; itemIndex < toIndex; itemIndex++) {
            HomeMediaLibraryItem item = homeItems.get(itemIndex);
            if (appended) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(escapeJson(itemButtonText(itemIndex + 1, item)))
                    .append("\",\"callback_data\":\"")
                    .append(escapeJson(itemCallbackData(item, homeDownloadLinkService)))
                    .append("\"}]");
            appended = true;
        }
        return appended;
    }

    private boolean appendHomeFileButtons(
            StringBuilder keyboard,
            boolean hasButton,
            List<HomeMediaLibraryFile> homeFiles,
            HomeDownloadLinkService homeDownloadLinkService,
            int page,
            int pageSize
    ) {
        if (homeFiles == null || homeFiles.isEmpty()) {
            return hasButton;
        }
        boolean appended = hasButton;
        int normalizedPage = normalizePage(homeFiles, page, pageSize);
        int fromIndex = normalizedPage * pageSize;
        int toIndex = Math.min(homeFiles.size(), fromIndex + pageSize);
        for (int fileIndex = fromIndex; fileIndex < toIndex; fileIndex++) {
            HomeMediaLibraryFile file = homeFiles.get(fileIndex);
            if (appended) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(escapeJson(fileButtonText(fileIndex + 1, file.fileName())))
                    .append("\",\"callback_data\":\"home:file:")
                    .append(homeDownloadLinkService.fileKey(file))
                    .append("\"}]");
            appended = true;
        }
        return appended;
    }

    private boolean appendS3FileButtons(
            StringBuilder keyboard,
            boolean hasButton,
            List<S3MediaLibraryFile> files,
            S3MediaLibraryService s3MediaLibraryService,
            int page,
            int pageSize
    ) {
        if (files == null || files.isEmpty()) {
            return hasButton;
        }
        boolean appended = hasButton;
        int normalizedPage = normalizePage(files, page, pageSize);
        int fromIndex = normalizedPage * pageSize;
        int toIndex = Math.min(files.size(), fromIndex + pageSize);
        for (int fileIndex = fromIndex; fileIndex < toIndex; fileIndex++) {
            S3MediaLibraryFile file = files.get(fileIndex);
            if (appended) {
                keyboard.append(",");
            }
            keyboard.append("[{\"text\":\"")
                    .append(escapeJson(fileButtonText(fileIndex + 1, file.fileName())))
                    .append("\",\"callback_data\":\"s3:file:")
                    .append(s3MediaLibraryService.fileKey(file))
                    .append("\"}]");
            appended = true;
        }
        return appended;
    }

    private boolean appendHomeLibraryNavigation(
            StringBuilder keyboard,
            boolean hasButton,
            List<HomeMediaLibraryItem> homeItems,
            int page,
            int pageSize
    ) {
        if (homeItems == null || homeItems.size() <= pageSize) {
            return hasButton;
        }
        int normalizedPage = normalizePage(homeItems, page, pageSize);
        int totalPages = totalPages(homeItems, pageSize);
        return appendPageNavigation(
                keyboard,
                hasButton,
                "menu:library:home:page:",
                normalizedPage,
                totalPages
        );
    }

    private boolean appendHomeFolderNavigation(
            StringBuilder keyboard,
            boolean hasButton,
            String folderKey,
            List<HomeMediaLibraryFile> folderFiles,
            int page,
            int pageSize
    ) {
        if (folderFiles == null || folderFiles.size() <= pageSize) {
            return hasButton;
        }
        int normalizedPage = normalizePage(folderFiles, page, pageSize);
        int totalPages = totalPages(folderFiles, pageSize);
        return appendPageNavigation(
                keyboard,
                hasButton,
                "menu:library:home:folder:" + folderKey + ":page:",
                normalizedPage,
                totalPages
        );
    }

    private boolean appendS3LibraryNavigation(
            StringBuilder keyboard,
            boolean hasButton,
            List<S3MediaLibraryFile> files,
            int page,
            int pageSize
    ) {
        if (files == null || files.size() <= pageSize) {
            return hasButton;
        }
        int normalizedPage = normalizePage(files, page, pageSize);
        int totalPages = totalPages(files, pageSize);
        return appendPageNavigation(
                keyboard,
                hasButton,
                "menu:library:s3:page:",
                normalizedPage,
                totalPages
        );
    }

    private boolean appendPageNavigation(
            StringBuilder keyboard,
            boolean hasButton,
            String callbackPrefix,
            int page,
            int totalPages
    ) {
        if (hasButton) {
            keyboard.append(",");
        }
        keyboard.append("[");
        boolean hasNavigationButton = false;
        if (page > 0) {
            keyboard.append("{\"text\":\"Назад\",\"callback_data\":\"")
                    .append(escapeJson(callbackPrefix))
                    .append(page - 1)
                    .append("\"}");
            hasNavigationButton = true;
        }
        if (page + 1 < totalPages) {
            if (hasNavigationButton) {
                keyboard.append(",");
            }
            keyboard.append("{\"text\":\"Вперёд\",\"callback_data\":\"")
                    .append(escapeJson(callbackPrefix))
                    .append(page + 1)
                    .append("\"}");
        }
        keyboard.append("]");
        return true;
    }

    private String itemButtonText(int index, HomeMediaLibraryItem item) {
        if (item.isFolder()) {
            return fitButtonText(index + ". Папка: " + item.displayName()
                    + " · " + item.fileCount() + " ф. · " + shortSize(item.totalSizeBytes()));
        }
        return fileButtonText(index, item.displayName());
    }

    private String itemCallbackData(HomeMediaLibraryItem item, HomeDownloadLinkService homeDownloadLinkService) {
        if (item.isFolder()) {
            return "menu:library:home:folder:" + item.folderKey() + ":page:0";
        }
        return "home:file:" + homeDownloadLinkService.fileKey(item.file());
    }

    private String fileButtonText(int index, String fileName) {
        String cleanName = fileName == null ? "файл" : fileName;
        return fitButtonText(index + ". " + cleanName);
    }

    private String fitButtonText(String text) {
        if (text.length() > 52) {
            return text.substring(0, 49) + "...";
        }
        return text;
    }

    private String shortSize(long bytes) {
        double gibibytes = bytes / 1024.0 / 1024.0 / 1024.0;
        if (gibibytes >= 1.0) {
            return String.format(Locale.US, "%.1f GB", gibibytes);
        }
        double mebibytes = bytes / 1024.0 / 1024.0;
        return String.format(Locale.US, "%.0f MB", mebibytes);
    }

    private boolean appendUrlButton(StringBuilder keyboard, boolean hasButton, String text, String url) {
        if (url == null || url.isBlank()) {
            return hasButton;
        }
        if (hasButton) {
            keyboard.append(",");
        }
        keyboard.append("[{\"text\":\"")
                .append(escapeJson(text))
                .append("\",\"url\":\"")
                .append(escapeJson(url))
                .append("\"}]");
        return true;
    }

    private int normalizePage(List<?> items, int page, int pageSize) {
        int totalPages = totalPages(items, pageSize);
        if (page < 0) {
            return 0;
        }
        if (page >= totalPages) {
            return totalPages - 1;
        }
        return page;
    }

    private int totalPages(List<?> items, int pageSize) {
        if (items == null || items.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil((double) items.size() / pageSize);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
