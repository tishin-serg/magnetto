package ru.xataaa.torrentbot.telegram;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.common.DiskSpaceService;
import ru.xataaa.torrentbot.common.FileSizeFormatter;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.downloadlink.HomeDownloadLinkService;
import ru.xataaa.torrentbot.media.HomeMediaLibraryFile;
import ru.xataaa.torrentbot.media.HomeMediaLibraryItem;
import ru.xataaa.torrentbot.media.HomeWebdavMediaLibraryService;
import ru.xataaa.torrentbot.media.MediaLibraryFile;
import ru.xataaa.torrentbot.media.MediaLibraryService;
import ru.xataaa.torrentbot.media.S3MediaLibraryFile;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Component
@RequiredArgsConstructor
@Slf4j
public class MenuCallbackHandler implements TelegramCallbackHandler {

    private static final int HOME_LIBRARY_PAGE_SIZE = 10;
    private static final int HOME_FOLDER_PAGE_SIZE = 10;
    private static final int S3_LIBRARY_PAGE_SIZE = 10;
    private static final int VPS_LIBRARY_TEXT_LIMIT = 15;

    private final TelegramMessageService telegramMessageService;
    private final TelegramKeyboardFactory telegramKeyboardFactory;
    private final DiskSpaceService diskSpaceService;
    private final FileSizeFormatter fileSizeFormatter;
    private final MediaLibraryService mediaLibraryService;
    private final HomeWebdavMediaLibraryService homeWebdavMediaLibraryService;
    private final S3MediaLibraryService s3MediaLibraryService;
    private final HomeDownloadLinkService homeDownloadLinkService;
    private final TimeProvider timeProvider;
    private final TaskOverviewService taskOverviewService;

    @org.springframework.beans.factory.annotation.Autowired
    private MovieDownloadConfirmationService confirmations;
    @org.springframework.beans.factory.annotation.Autowired
    private MediaCleanupCallbackHandler cleanup;
    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith("menu:");
    }

    @Override
    public void handle(String callbackQueryId, Long chatId, Long messageId, String data) {
        telegramMessageService.answerCallbackQuery(callbackQueryId, "");
        if (cleanup != null) cleanup.cancelPending(chatId);
        if (confirmations != null) confirmations.leaveInput(chatId);
        if ("menu:settings".equals(data)) { confirmations.settings(chatId, messageId); return; }
        if ("menu:libraries".equals(data)) {
            editOrSend(chatId, messageId, "🎬 Медиатека\n\nГде находятся скачанные фильмы?", telegramKeyboardFactory.libraryMenuKeyboard());
            return;
        }
        if ("menu:help".equals(data)) {
            editOrSend(chatId, messageId, helpText(), telegramKeyboardFactory.helpKeyboard());
            return;
        }
        if ("menu:search".equals(data)) {
            log.info("search_opened: chatId={}", chatId);
            editOrSend(chatId, messageId, searchHelpText(), telegramKeyboardFactory.searchLauncherKeyboard());
            return;
        }
        if ("menu:iphone".equals(data)) {
            editOrSend(chatId, messageId, iphoneInstruction(), telegramKeyboardFactory.backToMenuKeyboard());
            return;
        }
        if ("menu:space".equals(data)) {
            editOrSend(chatId, messageId, diskSpaceText(), telegramKeyboardFactory.libraryRecoveryKeyboard("menu:space"));
            return;
        }
        if ("menu:tasks".equals(data)) {
            editOrSend(chatId, messageId, taskOverviewService.text(), taskOverviewService.keyboard());
            return;
        }
        if (isHomeFolderCallback(data)) {
            handleHomeFolder(chatId, messageId, data);
            return;
        }
        if (isS3LibraryCallback(data)) {
            handleS3Library(chatId, messageId, data);
            return;
        }
        if (isHomeLibraryCallback(data)) {
            handleHomeLibrary(chatId, messageId, data);
            return;
        }
        if ("menu:library:vps".equals(data)) {
            String keyboard = mediaLibraryService.publicWebdavUrl() == null || mediaLibraryService.publicWebdavUrl().isBlank()
                    ? telegramKeyboardFactory.libraryRecoveryKeyboard("menu:library:vps")
                    : telegramKeyboardFactory.vpsLibraryKeyboard(mediaLibraryService.publicWebdavUrl());
            editOrSend(chatId, messageId, vpsMediaLibraryText(), keyboard);
            return;
        }
        editOrSend(chatId, messageId, mainMenuText(), telegramKeyboardFactory.mainMenuKeyboard());
    }

    public String mainMenuText() {
        return """
                🎬 Magnetto

                Найду и скачаю фильмы и сериалы. Готовые файлы — в медиатеке.

                Отправь название или выбери «🔎 Поиск» для подсказок.
                """;
    }

    public String helpText() {
        return """
                Помощь

                Поиск — название фильма или сериала, например «Матрица 1999». Можно отправить magnet-ссылку.
                Загрузки — ход скачивания, пауза и продолжение.
                Медиатека — готовые файлы и ссылки для просмотра.
                Настройки — размер, доступность раздач и место скачивания.

                /start — главное меню · /cancel — выйти из ввода
                /search название — поиск раздач · /tasks — загрузки
                /library — медиатека · /settings — настройки
                """;
    }

    public String mediaLibraryText() {
        return homeMediaLibraryText(homeItemsForKeyboard(), 0) + "\n\n" + vpsMediaLibraryText();
    }

    public String mediaLibraryKeyboard() {
        List<HomeMediaLibraryItem> homeItems = homeItemsForKeyboard();
        return telegramKeyboardFactory.homeMediaLibraryKeyboard(
                homeWebdavUrl(),
                homeLocalWebdavUrl(),
                mediaLibraryService.publicWebdavUrl(),
                homeItems,
                homeDownloadLinkService,
                normalizePage(homeItems, 0, HOME_LIBRARY_PAGE_SIZE),
                HOME_LIBRARY_PAGE_SIZE
        );
    }

    public String homeMediaLibraryText(List<HomeMediaLibraryFile> homeFiles) {
        return homeMediaLibraryText(homeWebdavMediaLibraryService.groupFiles(homeFiles), 0);
    }

    public String homeMediaLibraryText(List<HomeMediaLibraryItem> homeItems, int requestedPage) {
        StringBuilder text = new StringBuilder();
        appendHomeLibrary(text, homeItems, requestedPage);
        if (text.isEmpty()) {
            return "Домашняя медиатека пока пустая.";
        }
        return text.toString().trim();
    }

    public String vpsMediaLibraryText() {
        StringBuilder text = new StringBuilder();
        appendVpsLibrary(text);
        if (text.isEmpty()) {
            return "Медиатека · VPS\n\nФильмов пока нет. Найди фильм и выбери скачивание на VPS.";
        }
        return text.toString().trim();
    }

    private void handleHomeLibrary(Long chatId, Long messageId, String data) {
        int page = homeLibraryPage(data);
        List<HomeMediaLibraryItem> homeItems;
        try {
            homeItems = homeWebdavMediaLibraryService.isEnabled() ? homeWebdavMediaLibraryService.listItems() : List.of();
        } catch (RuntimeException exception) {
            editOrSend(chatId, messageId, "Домашний ПК недоступен.\n\nПроверь, что он включён и подключён к сети, затем обнови список.",
                    telegramKeyboardFactory.libraryRecoveryKeyboard("menu:library:home"));
            return;
        }
        if (homeItems.isEmpty()) {
            editOrSend(chatId, messageId, homeWebdavMediaLibraryService.isEnabled()
                    ? "Домашняя медиатека\n\nФильмов пока нет. Найди фильм и выбери скачивание на домашний ПК."
                    : "Домашняя медиатека не подключена. Выбери другое хранилище.",
                    telegramKeyboardFactory.libraryRecoveryKeyboard("menu:library:home"));
            return;
        }
        editOrSend(
                chatId,
                messageId,
                homeMediaLibraryText(homeItems, page),
                telegramKeyboardFactory.homeMediaLibraryKeyboard(
                        homeWebdavUrl(),
                        homeLocalWebdavUrl(),
                        mediaLibraryService.publicWebdavUrl(),
                        homeItems,
                        homeDownloadLinkService,
                        normalizePage(homeItems, page, HOME_LIBRARY_PAGE_SIZE),
                        HOME_LIBRARY_PAGE_SIZE
                )
        );
    }

    private void handleHomeFolder(Long chatId, Long messageId, String data) {
        String folderKey = homeFolderKey(data);
        int page = homeFolderPage(data);
        List<HomeMediaLibraryFile> folderFiles;
        try {
            folderFiles = homeWebdavMediaLibraryService.listFilesInFolder(folderKey);
        } catch (RuntimeException exception) {
            editOrSend(chatId, messageId, "Не удалось открыть папку. Проверь подключение домашнего ПК и обнови медиатеку.",
                    telegramKeyboardFactory.libraryRecoveryKeyboard("menu:library:home"));
            return;
        }
        editOrSend(
                chatId,
                messageId,
                homeFolderText(folderKey, folderFiles, page),
                telegramKeyboardFactory.homeFolderKeyboard(
                        folderKey,
                        folderFiles,
                        homeDownloadLinkService,
                        normalizePage(folderFiles, page, HOME_FOLDER_PAGE_SIZE),
                        HOME_FOLDER_PAGE_SIZE
                )
        );
    }

    private void handleS3Library(Long chatId, Long messageId, String data) {
        int page = s3LibraryPage(data);
        List<S3MediaLibraryFile> files;
        try {
            files = s3MediaLibraryService.isEnabled() ? s3MediaLibraryService.listFiles() : List.of();
        } catch (RuntimeException exception) {
            editOrSend(chatId, messageId, "Облако S3 временно недоступно. Попробуй обновить список позже.",
                    telegramKeyboardFactory.libraryRecoveryKeyboard("menu:library:s3"));
            return;
        }
        if (files.isEmpty()) {
            editOrSend(chatId, messageId, s3MediaLibraryService.isEnabled()
                    ? "Облако · S3\n\nФильмов пока нет. Найди фильм и выбери скачивание в S3."
                    : "Облако S3 не подключено. Выбери другое хранилище.",
                    telegramKeyboardFactory.libraryRecoveryKeyboard("menu:library:s3"));
            return;
        }
        editOrSend(
                chatId,
                messageId,
                s3MediaLibraryText(files, page),
                telegramKeyboardFactory.s3MediaLibraryKeyboard(
                        files,
                        s3MediaLibraryService,
                        normalizePage(files, page, S3_LIBRARY_PAGE_SIZE),
                        S3_LIBRARY_PAGE_SIZE
                )
        );
    }

    private boolean isHomeLibraryCallback(String data) {
        return "menu:library".equals(data)
                || "menu:library:home".equals(data)
                || data.startsWith("menu:library:home:page:");
    }

    private boolean isHomeFolderCallback(String data) {
        return data != null && data.startsWith("menu:library:home:folder:");
    }

    private boolean isS3LibraryCallback(String data) {
        return "menu:library:s3".equals(data)
                || data.startsWith("menu:library:s3:page:");
    }

    private String searchHelpText() {
        return """
                🔎 Поиск

                Напиши название фильма или сериала, например «Матрица 1999».
                Поиск с подсказками поможет выбрать точный фильм по названию и году.
                """;
    }

    public String iphoneInstruction() {
        return """
                Как скачивать большие фильмы на iPhone:

                Способ 1. Быстро и без Tailscale на телефоне:
                1. Открой домашнюю медиатеку.
                2. Выбери конкретный файл.
                3. Нажми «Скачать на iPhone».
                4. Открой временную ссылку и сохрани файл.

                Способ 2. Через Infuse и WebDAV:
                1. Открой Infuse.
                2. Add Files / Shares.
                3. Выбери WebDAV.
                4. Подключи домашнюю папку через Tailscale или по домашнему Wi-Fi.
                5. Найди фильм и нажми Download.

                Перед поездкой проверь, что фильм реально скачан офлайн.
                """;
    }

    public String diskSpaceText() {
        DiskSpaceService.DiskSpaceInfo diskSpaceInfo = diskSpaceService.downloadStorageInfo();
        return "Место на сервере:\n\n"
                + "Свободно: " + fileSizeFormatter.format(diskSpaceInfo.usableBytes()) + "\n"
                + "Всего: " + fileSizeFormatter.format(diskSpaceInfo.totalBytes()) + "\n\n"
                + "Если размер выбранной раздачи больше свободного места, я не начну скачивание на VPS.";
    }

    private String homeWebdavUrl() {
        if (!homeWebdavMediaLibraryService.isEnabled()) {
            return "";
        }
        return homeWebdavMediaLibraryService.baseUrl();
    }

    private String homeLocalWebdavUrl() {
        if (!homeWebdavMediaLibraryService.isEnabled()) {
            return "";
        }
        return homeWebdavMediaLibraryService.localBaseUrl();
    }

    private List<HomeMediaLibraryItem> homeItemsForKeyboard() {
        if (!homeWebdavMediaLibraryService.isEnabled()) {
            return List.of();
        }
        try {
            return homeWebdavMediaLibraryService.listItems();
        } catch (RetryableOperationException exception) {
            log.warn("Home WebDAV media library is temporarily unavailable: error={}", exception.getMessage());
            return List.of();
        } catch (RuntimeException exception) {
            log.warn("Home WebDAV media library listing failed: error={}", exception.getMessage(), exception);
            return List.of();
        }
    }

    private List<HomeMediaLibraryFile> homeFilesInFolder(String folderKey) {
        if (!homeWebdavMediaLibraryService.isEnabled()) {
            return List.of();
        }
        try {
            return homeWebdavMediaLibraryService.listFilesInFolder(folderKey);
        } catch (RetryableOperationException exception) {
            log.warn("Home WebDAV folder is temporarily unavailable: folderKey={}, error={}", folderKey, exception.getMessage());
            return List.of();
        } catch (RuntimeException exception) {
            log.warn("Home WebDAV folder listing failed: folderKey={}, error={}", folderKey, exception.getMessage(), exception);
            return List.of();
        }
    }

    private List<S3MediaLibraryFile> s3FilesForKeyboard() {
        if (!s3MediaLibraryService.isEnabled()) {
            return List.of();
        }
        try {
            return s3MediaLibraryService.listFiles();
        } catch (RetryableOperationException exception) {
            log.warn("S3 media library is temporarily unavailable: error={}", exception.getMessage());
            return List.of();
        } catch (RuntimeException exception) {
            log.warn("S3 media library listing failed: error={}", exception.getMessage(), exception);
            return List.of();
        }
    }

    private String s3MediaLibraryText(List<S3MediaLibraryFile> files, int requestedPage) {
        if (!s3MediaLibraryService.isEnabled()) {
            return "S3 медиатека выключена в конфиге бота.";
        }
        StringBuilder text = new StringBuilder();
        text.append("S3 медиатека\n");
        if (files.isEmpty()) {
            text.append("\nПока пусто или S3 временно недоступен.");
            return text.toString();
        }
        long totalBytes = 0L;
        for (S3MediaLibraryFile file : files) {
            totalBytes += file.sizeBytes();
        }
        int page = normalizePage(files, requestedPage, S3_LIBRARY_PAGE_SIZE);
        int totalPages = totalPages(files, S3_LIBRARY_PAGE_SIZE);
        int fromIndex = page * S3_LIBRARY_PAGE_SIZE;
        int toIndex = Math.min(files.size(), fromIndex + S3_LIBRARY_PAGE_SIZE);
        text.append("Файлов: ")
                .append(files.size())
                .append("\n")
                .append("Занято: ")
                .append(fileSizeFormatter.format(totalBytes))
                .append("\n")
                .append("Страница: ")
                .append(page + 1)
                .append(" из ")
                .append(totalPages)
                .append("\n")
                .append("Сортировка: сначала новые")
                .append("\n\n")
                .append("Выбери файл кнопкой ниже:\n\n");
        for (int fileIndex = fromIndex; fileIndex < toIndex; fileIndex++) {
            S3MediaLibraryFile file = files.get(fileIndex);
            text.append(fileIndex + 1)
                    .append(". ")
                    .append(file.fileName())
                    .append("\n   ")
                    .append(fileSizeFormatter.format(file.sizeBytes()));
            if (file.modifiedAt() != null) {
                text.append(", ").append(timeProvider.formatDateTime(file.modifiedAt()));
            }
            text.append("\n");
        }
        return text.toString().trim();
    }

    private void appendHomeLibrary(StringBuilder text, List<HomeMediaLibraryItem> items, int requestedPage) {
        if (!homeWebdavMediaLibraryService.isEnabled()) {
            return;
        }
        text.append("Домашняя медиатека\n");
        if (items.isEmpty()) {
            text.append("Пока пусто или домашний WebDAV недоступен.\n\n");
            return;
        }
        long totalBytes = 0L;
        int folderCount = 0;
        int directFileCount = 0;
        int totalFileCount = 0;
        for (HomeMediaLibraryItem item : items) {
            totalBytes += item.totalSizeBytes();
            totalFileCount += item.fileCount();
            if (item.isFolder()) {
                folderCount++;
            } else {
                directFileCount++;
            }
        }
        int page = normalizePage(items, requestedPage, HOME_LIBRARY_PAGE_SIZE);
        int totalPages = totalPages(items, HOME_LIBRARY_PAGE_SIZE);
        int fromIndex = page * HOME_LIBRARY_PAGE_SIZE;
        int toIndex = Math.min(items.size(), fromIndex + HOME_LIBRARY_PAGE_SIZE);
        text.append("Папок/сериалов: ")
                .append(folderCount)
                .append("\n")
                .append("Фильмов в корне: ")
                .append(directFileCount)
                .append("\n")
                .append("Файлов всего: ")
                .append(totalFileCount)
                .append("\n")
                .append("Занято: ")
                .append(fileSizeFormatter.format(totalBytes))
                .append("\n")
                .append("Страница: ")
                .append(page + 1)
                .append(" из ")
                .append(totalPages)
                .append("\n\n")
                .append("Выбери папку сериала или отдельный фильм кнопкой ниже:\n\n");
        for (int itemIndex = fromIndex; itemIndex < toIndex; itemIndex++) {
            HomeMediaLibraryItem item = items.get(itemIndex);
            text.append(itemIndex + 1)
                    .append(". ");
            if (item.isFolder()) {
                text.append("[папка] ");
            }
            text.append(item.displayName())
                    .append("\n   ");
            if (item.isFolder()) {
                text.append(item.fileCount()).append(" файлов, ");
            }
            text.append(fileSizeFormatter.format(item.totalSizeBytes()));
            if (item.latestModifiedAt() != null) {
                text.append(", ").append(timeProvider.formatDateTime(item.latestModifiedAt()));
            }
            text.append("\n");
        }
    }

    private String homeFolderText(String folderKey, List<HomeMediaLibraryFile> files, int requestedPage) {
        String folderName = homeWebdavMediaLibraryService.folderName(folderKey);
        StringBuilder text = new StringBuilder();
        text.append("Папка домашней медиатеки\n\n")
                .append(folderName.isBlank() ? folderKey : folderName)
                .append("\n");
        if (files.isEmpty()) {
            text.append("\nНе нашел файлы в этой папке. Обнови медиатеку и попробуй еще раз.");
            return text.toString();
        }
        long totalBytes = 0L;
        for (HomeMediaLibraryFile file : files) {
            totalBytes += file.sizeBytes();
        }
        int page = normalizePage(files, requestedPage, HOME_FOLDER_PAGE_SIZE);
        int totalPages = totalPages(files, HOME_FOLDER_PAGE_SIZE);
        int fromIndex = page * HOME_FOLDER_PAGE_SIZE;
        int toIndex = Math.min(files.size(), fromIndex + HOME_FOLDER_PAGE_SIZE);
        text.append("Файлов: ")
                .append(files.size())
                .append("\n")
                .append("Занято: ")
                .append(fileSizeFormatter.format(totalBytes))
                .append("\n")
                .append("Страница: ")
                .append(page + 1)
                .append(" из ")
                .append(totalPages)
                .append("\n\n")
                .append("Выбери конкретный файл кнопкой ниже:\n\n");
        for (int fileIndex = fromIndex; fileIndex < toIndex; fileIndex++) {
            HomeMediaLibraryFile file = files.get(fileIndex);
            text.append(fileIndex + 1)
                    .append(". ")
                    .append(file.fileName())
                    .append("\n   ")
                    .append(fileSizeFormatter.format(file.sizeBytes()));
            if (file.modifiedAt() != null) {
                text.append(", ").append(timeProvider.formatDateTime(file.modifiedAt()));
            }
            text.append("\n");
        }
        return text.toString().trim();
    }

    private void appendVpsLibrary(StringBuilder text) {
        List<MediaLibraryFile> files = mediaLibraryService.listFiles();
        if (files.isEmpty()) {
            return;
        }
        long totalBytes = 0L;
        for (MediaLibraryFile file : files) {
            totalBytes += file.sizeBytes();
        }
        text.append("VPS медиатека\n")
                .append("Файлов: ")
                .append(files.size())
                .append("\n")
                .append("Занято: ")
                .append(fileSizeFormatter.format(totalBytes))
                .append("\n\n");
        int index = 1;
        for (MediaLibraryFile file : files) {
            text.append(index)
                    .append(". ")
                    .append(file.fileName())
                    .append("\n   ")
                    .append(fileSizeFormatter.format(file.sizeBytes()))
                    .append("\n");
            index++;
            if (index > VPS_LIBRARY_TEXT_LIMIT) {
                text.append("\nПоказал первые ").append(VPS_LIBRARY_TEXT_LIMIT).append(" файлов.");
                break;
            }
        }
    }

    private int homeLibraryPage(String data) {
        String prefix = "menu:library:home:page:";
        if (!data.startsWith(prefix)) {
            return 0;
        }
        try {
            return Integer.parseInt(data.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String homeFolderKey(String data) {
        String prefix = "menu:library:home:folder:";
        if (!data.startsWith(prefix)) {
            return "";
        }
        String value = data.substring(prefix.length());
        int pageSeparatorIndex = value.indexOf(":page:");
        return pageSeparatorIndex >= 0 ? value.substring(0, pageSeparatorIndex) : value;
    }

    private int homeFolderPage(String data) {
        String marker = ":page:";
        int pageSeparatorIndex = data.indexOf(marker);
        if (pageSeparatorIndex < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(data.substring(pageSeparatorIndex + marker.length()));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int s3LibraryPage(String data) {
        String prefix = "menu:library:s3:page:";
        if (!data.startsWith(prefix)) {
            return 0;
        }
        try {
            return Integer.parseInt(data.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int normalizePage(List<?> items, int requestedPage, int pageSize) {
        int totalPages = totalPages(items, pageSize);
        if (requestedPage < 0) {
            return 0;
        }
        if (requestedPage >= totalPages) {
            return totalPages - 1;
        }
        return requestedPage;
    }

    private int totalPages(List<?> items, int pageSize) {
        if (items == null || items.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil((double) items.size() / pageSize);
    }

    private void editOrSend(Long chatId, Long messageId, String text, String keyboardJson) {
        if (messageId == null) {
            telegramMessageService.sendTextWithInlineKeyboard(chatId, text, keyboardJson);
            return;
        }
        telegramMessageService.editText(chatId, messageId, text, keyboardJson);
    }
}
