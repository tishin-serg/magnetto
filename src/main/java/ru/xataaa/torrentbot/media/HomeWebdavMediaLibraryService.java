package ru.xataaa.torrentbot.media;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.common.TimeProvider;
import ru.xataaa.torrentbot.config.HomeWebdavProperties;
import ru.xataaa.torrentbot.config.WebClientConfig;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.retry.RetryableOperationException;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeWebdavMediaLibraryService {

    private static final int MAX_RECURSION_DEPTH = 5;
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".webm");

    private static final Pattern RESPONSE_PATTERN = Pattern.compile(
            "<(?:d:)?response[^>]*>(.*?)</(?:d:)?response>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern HREF_PATTERN = Pattern.compile("<(?:d:)?href>(.*?)</(?:d:)?href>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CONTENT_LENGTH_PATTERN = Pattern.compile(
            "<(?:d:)?getcontentlength>(\\d+)</(?:d:)?getcontentlength>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LAST_MODIFIED_PATTERN = Pattern.compile(
            "<(?:d:)?getlastmodified>(.*?)</(?:d:)?getlastmodified>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final HomeWebdavProperties homeWebdavProperties;
    private final WebClient.Builder webClientBuilder;

    public boolean isEnabled() {
        return homeWebdavProperties.enabled();
    }

    public String baseUrl() {
        return ensureTrailingSlash(homeWebdavProperties.baseUrl());
    }

    public String localBaseUrl() {
        return ensureTrailingSlash(homeWebdavProperties.localBaseUrl());
    }

    public List<HomeMediaLibraryFile> listFiles() {
        if (!homeWebdavProperties.enabled()) {
            return List.of();
        }
        List<HomeMediaLibraryFile> files = new ArrayList<>();
        collectFiles("/", 0, new HashSet<>(), files);
        files.sort(Comparator.comparing(HomeMediaLibraryFile::modifiedAt).reversed());
        log.info("Home WebDAV media library listed: files={}", files.size());
        return files;
    }

    public List<HomeMediaLibraryItem> listItems() {
        return groupFiles(listFiles());
    }

    public List<HomeMediaLibraryFile> listFilesInFolder(String folderKey) {
        if (folderKey == null || folderKey.isBlank()) {
            return List.of();
        }
        List<HomeMediaLibraryFile> folderFiles = new ArrayList<>();
        for (HomeMediaLibraryFile file : listFiles()) {
            String folderPath = folderPath(file.relativePath());
            if (!folderPath.isBlank() && folderKey(folderPath).equals(folderKey)) {
                folderFiles.add(file);
            }
        }
        folderFiles.sort(Comparator.comparing(HomeMediaLibraryFile::relativePath));
        return folderFiles;
    }

    public String folderName(String folderKey) {
        for (HomeMediaLibraryItem item : listItems()) {
            if (item.isFolder() && item.folderKey().equals(folderKey)) {
                return item.displayName();
            }
        }
        return "";
    }

    public String folderKey(String folderPath) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(folderPath.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return encoded.substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public List<HomeMediaLibraryItem> groupFiles(List<HomeMediaLibraryFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<HomeMediaLibraryItem> items = new ArrayList<>();
        Map<String, FolderAccumulator> folders = new LinkedHashMap<>();
        for (HomeMediaLibraryFile file : files) {
            String folderPath = folderPath(file.relativePath());
            if (folderPath.isBlank()) {
                items.add(new HomeMediaLibraryItem(
                        HomeMediaLibraryItemType.DIRECT_FILE,
                        file.fileName(),
                        "",
                        "",
                        file,
                        1,
                        file.sizeBytes(),
                        file.modifiedAt()
                ));
                continue;
            }
            FolderAccumulator folderAccumulator = folders.computeIfAbsent(folderPath, FolderAccumulator::new);
            folderAccumulator.add(file);
        }
        for (FolderAccumulator folderAccumulator : folders.values()) {
            items.add(folderAccumulator.toItem(folderKey(folderAccumulator.folderPath())));
        }
        items.sort(Comparator.comparing(HomeMediaLibraryItem::latestModifiedAt).reversed());
        return items;
    }

    public String tailscaleFileUrl(String fileName) {
        return joinUrl(baseUrl(), fileName);
    }

    public String localWifiFileUrl(String fileName) {
        String localBaseUrl = localBaseUrl();
        if (localBaseUrl.isBlank()) {
            return "";
        }
        return joinUrl(localBaseUrl, fileName);
    }

    List<HomeMediaLibraryFile> parseFiles(String listingXml) {
        if (listingXml == null || listingXml.isBlank()) {
            return List.of();
        }
        List<HomeMediaLibraryFile> files = new ArrayList<>();
        Matcher responseMatcher = RESPONSE_PATTERN.matcher(listingXml);
        while (responseMatcher.find()) {
            String responseXml = responseMatcher.group(1);
            String href = firstMatch(HREF_PATTERN, responseXml);
            if (!isFileHref(href)) {
                continue;
            }
            String fileName = fileNameFromHref(href);
            String relativePath = relativePathFromHref(href);
            if (fileName.isBlank() || relativePath.isBlank()) {
                continue;
            }
            if (!isVisibleMediaFile(fileName)) {
                continue;
            }
            long sizeBytes = parseLong(firstMatch(CONTENT_LENGTH_PATTERN, responseXml));
            LocalDateTime modifiedAt = parseModifiedAt(firstMatch(LAST_MODIFIED_PATTERN, responseXml));
            files.add(new HomeMediaLibraryFile(
                    fileName,
                    relativePath,
                    sizeBytes,
                    modifiedAt,
                    tailscaleFileUrl(relativePath),
                    localWifiFileUrl(relativePath)
            ));
        }
        return files;
    }

    private void collectFiles(String directoryHref, int depth, Set<String> visitedDirectories, List<HomeMediaLibraryFile> files) {
        if (depth > MAX_RECURSION_DEPTH || !visitedDirectories.add(directoryHref)) {
            return;
        }
        String listingXml = listDirectory(directoryHref);
        files.addAll(parseFiles(listingXml));
        for (String childDirectoryHref : parseDirectoryHrefs(listingXml, directoryHref)) {
            try {
                collectFiles(childDirectoryHref, depth + 1, visitedDirectories, files);
            } catch (RetryableOperationException retryableOperationException) {
                log.warn("Skipping unavailable home WebDAV directory: href={}, error={}", childDirectoryHref, retryableOperationException.getMessage());
            } catch (IllegalArgumentException illegalArgumentException) {
                log.warn("Skipping invalid home WebDAV directory href: href={}, error={}", childDirectoryHref, illegalArgumentException.getMessage());
            }
        }
    }

    List<String> parseDirectoryHrefs(String listingXml, String currentDirectoryHref) {
        if (listingXml == null || listingXml.isBlank()) {
            return List.of();
        }
        String normalizedCurrentDirectoryHref = normalizeDirectoryHref(currentDirectoryHref);
        List<String> directories = new ArrayList<>();
        Matcher responseMatcher = RESPONSE_PATTERN.matcher(listingXml);
        while (responseMatcher.find()) {
            String responseXml = responseMatcher.group(1);
            String href = firstMatch(HREF_PATTERN, responseXml);
            if (!isDirectoryHref(href)) {
                continue;
            }
            String normalizedHref = normalizeDirectoryHref(href);
            if (normalizedHref.equals(normalizedCurrentDirectoryHref)) {
                continue;
            }
            directories.add(normalizedHref);
        }
        return directories;
    }

    private String listDirectory(String directoryHref) {
        String responseBody = webClient().method(HttpMethod.valueOf("PROPFIND"))
                .uri(encodeDirectoryHref(directoryHref))
                .header("Depth", "1")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                .retrieve()
                .bodyToMono(String.class)
                .onErrorMap(throwable -> new RetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Failed to list home WebDAV", throwable))
                .block(Duration.ofMillis(homeWebdavProperties.requestTimeoutMs()));
        return responseBody == null ? "" : responseBody;
    }

    private WebClient webClient() {
        return webClientBuilder
                .baseUrl(homeWebdavProperties.baseUrl())
                .clientConnector(WebClientConfig.connector(homeWebdavProperties.connectTimeoutMs(), homeWebdavProperties.requestTimeoutMs()))
                .build();
    }

    private String authorizationHeader() {
        if (homeWebdavProperties.username() == null || homeWebdavProperties.username().isBlank()) {
            throw new NonRetryableOperationException(ErrorCode.CLEANUP_FAILED, "Home WebDAV username is empty");
        }
        String credentials = homeWebdavProperties.username() + ":" + homeWebdavProperties.password();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isFileHref(String href) {
        if (href == null || href.isBlank() || href.endsWith("/")) {
            return false;
        }
        return !href.contains("..");
    }

    private boolean isDirectoryHref(String href) {
        if (href == null || href.isBlank() || !href.endsWith("/")) {
            return false;
        }
        return !href.contains("..");
    }

    private boolean isVisibleMediaFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        for (String videoExtension : VIDEO_EXTENSIONS) {
            if (lowerFileName.endsWith(videoExtension)) {
                return true;
            }
        }
        return false;
    }

    private String fileNameFromHref(String href) {
        String path = hrefPath(href);
        int lastSlashIndex = path.lastIndexOf('/');
        String encodedFileName = lastSlashIndex >= 0 ? path.substring(lastSlashIndex + 1) : path;
        return URLDecoder.decode(encodedFileName, StandardCharsets.UTF_8);
    }

    private String relativePathFromHref(String href) {
        String path = hrefPath(href);
        String withoutLeadingSlash = path.startsWith("/") ? path.substring(1) : path;
        return URLDecoder.decode(withoutLeadingSlash, StandardCharsets.UTF_8);
    }

    private String hrefPath(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        String trimmedHref = href.trim();
        int queryIndex = trimmedHref.indexOf('?');
        if (queryIndex >= 0) {
            trimmedHref = trimmedHref.substring(0, queryIndex);
        }
        int fragmentIndex = trimmedHref.indexOf('#');
        if (fragmentIndex >= 0) {
            trimmedHref = trimmedHref.substring(0, fragmentIndex);
        }
        if (trimmedHref.startsWith("http://") || trimmedHref.startsWith("https://")) {
            try {
                return java.net.URI.create(trimmedHref.replace(" ", "%20")).getRawPath();
            } catch (IllegalArgumentException ignored) {
                int pathStartIndex = trimmedHref.indexOf('/', trimmedHref.indexOf("://") + 3);
                return pathStartIndex >= 0 ? trimmedHref.substring(pathStartIndex) : "";
            }
        }
        return trimmedHref;
    }

    private String firstMatch(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim();
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException numberFormatException) {
            return 0L;
        }
    }

    private LocalDateTime parseModifiedAt(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now(TimeProvider.MOSCOW_ZONE_ID);
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .atZoneSameInstant(TimeProvider.MOSCOW_ZONE_ID)
                    .toLocalDateTime();
        } catch (DateTimeParseException dateTimeParseException) {
            return LocalDateTime.now(TimeProvider.MOSCOW_ZONE_ID);
        }
    }

    private String joinUrl(String baseUrl, String fileName) {
        String normalizedBaseUrl = ensureTrailingSlash(baseUrl);
        if (normalizedBaseUrl.isBlank()) {
            return "";
        }
        return normalizedBaseUrl + encodeRelativePath(fileName);
    }

    private String encodeRelativePath(String relativePath) {
        String[] pathParts = relativePath.split("/");
        List<String> encodedPathParts = new ArrayList<>();
        for (String pathPart : pathParts) {
            if (pathPart.isBlank()) {
                continue;
            }
            encodedPathParts.add(URLEncoder.encode(pathPart, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("/", encodedPathParts);
    }

    private String normalizeDirectoryHref(String href) {
        if (href == null || href.isBlank()) {
            return "/";
        }
        String path = hrefPath(href);
        String normalizedHref = path.startsWith("/") ? path : "/" + path;
        return normalizedHref.endsWith("/") ? normalizedHref : normalizedHref + "/";
    }

    private String encodeDirectoryHref(String directoryHref) {
        String normalizedHref = normalizeDirectoryHref(directoryHref);
        if ("/".equals(normalizedHref)) {
            return "/";
        }
        String withoutLeadingSlash = normalizedHref.startsWith("/") ? normalizedHref.substring(1) : normalizedHref;
        String withoutTrailingSlash = withoutLeadingSlash.endsWith("/")
                ? withoutLeadingSlash.substring(0, withoutLeadingSlash.length() - 1)
                : withoutLeadingSlash;
        String decodedPath = decodePathSafely(withoutTrailingSlash);
        return "/" + encodeRelativePath(decodedPath) + "/";
    }

    private String decodePathSafely(String path) {
        try {
            return URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException illegalArgumentException) {
            return path;
        }
    }

    private String ensureTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url : url + "/";
    }

    private String folderPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        int slashIndex = relativePath.indexOf('/');
        if (slashIndex <= 0) {
            return "";
        }
        return relativePath.substring(0, slashIndex);
    }

    private static class FolderAccumulator {
        private final String folderPath;
        private int fileCount;
        private long totalSizeBytes;
        private LocalDateTime latestModifiedAt;

        private FolderAccumulator(String folderPath) {
            this.folderPath = folderPath;
        }

        private void add(HomeMediaLibraryFile file) {
            fileCount++;
            totalSizeBytes += file.sizeBytes();
            if (latestModifiedAt == null || file.modifiedAt().isAfter(latestModifiedAt)) {
                latestModifiedAt = file.modifiedAt();
            }
        }

        private String folderPath() {
            return folderPath;
        }

        private HomeMediaLibraryItem toItem(String folderKey) {
            return new HomeMediaLibraryItem(
                    HomeMediaLibraryItemType.FOLDER,
                    folderPath,
                    folderPath,
                    folderKey,
                    null,
                    fileCount,
                    totalSizeBytes,
                    latestModifiedAt
            );
        }
    }
}
