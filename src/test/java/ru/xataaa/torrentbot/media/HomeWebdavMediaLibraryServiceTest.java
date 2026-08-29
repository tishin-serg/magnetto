package ru.xataaa.torrentbot.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.xataaa.torrentbot.config.HomeWebdavProperties;

class HomeWebdavMediaLibraryServiceTest {

    @Test
    void shouldParseWebdavFilesAndBuildUrls() {
        HomeWebdavMediaLibraryService service = service();
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:multistatus xmlns:d="DAV:">
                  <d:response>
                    <d:href>/</d:href>
                    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
                  </d:response>
                  <d:response>
                    <d:href>/The.Matrix%20Reloaded.mkv</d:href>
                    <d:propstat>
                      <d:prop>
                        <d:getcontentlength>42356046509</d:getcontentlength>
                        <d:getlastmodified>Tue, 09 Jun 2026 20:00:44 GMT</d:getlastmodified>
                      </d:prop>
                    </d:propstat>
                  </d:response>
                </d:multistatus>
                """;

        List<HomeMediaLibraryFile> files = service.parseFiles(xml);

        assertThat(files).hasSize(1);
        HomeMediaLibraryFile file = files.get(0);
        assertThat(file.fileName()).isEqualTo("The.Matrix Reloaded.mkv");
        assertThat(file.relativePath()).isEqualTo("The.Matrix Reloaded.mkv");
        assertThat(file.sizeBytes()).isEqualTo(42356046509L);
        assertThat(file.tailscaleUrl()).isEqualTo("http://100.123.82.79:8085/The.Matrix%20Reloaded.mkv");
        assertThat(file.localWifiUrl()).isEqualTo("http://192.168.1.189:8085/The.Matrix%20Reloaded.mkv");
    }

    @Test
    void shouldKeepRelativePathForNestedFiles() {
        HomeWebdavMediaLibraryService service = service();
        String xml = """
                <d:multistatus xmlns:d="DAV:">
                  <d:response>
                    <d:href>/Serial/Serial.S01E01.mkv</d:href>
                    <d:propstat><d:prop><d:getcontentlength>100</d:getcontentlength></d:prop></d:propstat>
                  </d:response>
                </d:multistatus>
                """;

        List<HomeMediaLibraryFile> files = service.parseFiles(xml);

        assertThat(files).hasSize(1);
        HomeMediaLibraryFile file = files.get(0);
        assertThat(file.fileName()).isEqualTo("Serial.S01E01.mkv");
        assertThat(file.relativePath()).isEqualTo("Serial/Serial.S01E01.mkv");
        assertThat(file.tailscaleUrl()).isEqualTo("http://100.123.82.79:8085/Serial/Serial.S01E01.mkv");
    }

    @Test
    void shouldParseHrefWithRawSpaces() {
        HomeWebdavMediaLibraryService service = service();
        String xml = """
                <d:multistatus xmlns:d="DAV:">
                  <d:response>
                    <d:href>/Van Helsing (2004) Fullscreen DVDRip.mkv</d:href>
                    <d:propstat><d:prop><d:getcontentlength>100</d:getcontentlength></d:prop></d:propstat>
                  </d:response>
                </d:multistatus>
                """;

        List<HomeMediaLibraryFile> files = service.parseFiles(xml);

        assertThat(files).hasSize(1);
        HomeMediaLibraryFile file = files.get(0);
        assertThat(file.fileName()).isEqualTo("Van Helsing (2004) Fullscreen DVDRip.mkv");
        assertThat(file.relativePath()).isEqualTo("Van Helsing (2004) Fullscreen DVDRip.mkv");
        assertThat(file.tailscaleUrl()).isEqualTo("http://100.123.82.79:8085/Van%20Helsing%20%282004%29%20Fullscreen%20DVDRip.mkv");
    }

    @Test
    void shouldIgnoreCollectionAndUnsafeHref() {
        HomeWebdavMediaLibraryService service = service();
        String xml = """
                <d:multistatus xmlns:d="DAV:">
                  <d:response><d:href>/folder/</d:href></d:response>
                  <d:response><d:href>/../secret.mkv</d:href></d:response>
                  <d:response><d:href>/.0a8fd1709067a263.parts</d:href></d:response>
                </d:multistatus>
                """;

        assertThat(service.parseFiles(xml)).isEmpty();
    }

    @Test
    void shouldParseChildDirectoriesForRecursiveListing() {
        HomeWebdavMediaLibraryService service = service();
        String xml = """
                <d:multistatus xmlns:d="DAV:">
                  <d:response><d:href>/</d:href></d:response>
                  <d:response><d:href>/Serial/</d:href></d:response>
                  <d:response><d:href>/Movie.mkv</d:href></d:response>
                </d:multistatus>
                """;

        assertThat(service.parseDirectoryHrefs(xml, "/")).containsExactly("/Serial/");
    }

    @Test
    void shouldParseChildDirectoryWithRawSpacesAndCyrillic() {
        HomeWebdavMediaLibraryService service = service();
        String xml = """
                <d:multistatus xmlns:d="DAV:">
                  <d:response><d:href>/</d:href></d:response>
                  <d:response><d:href>/Бриджертоны.Bridgerton.S04.WEB-DLRip.Videofilm Int/</d:href></d:response>
                </d:multistatus>
                """;

        assertThat(service.parseDirectoryHrefs(xml, "/"))
                .containsExactly("/Бриджертоны.Bridgerton.S04.WEB-DLRip.Videofilm Int/");
    }

    @Test
    void shouldGroupRootMovieAsDirectFileAndSerialFolder() {
        HomeWebdavMediaLibraryService service = service();
        String xml = """
                <d:multistatus xmlns:d="DAV:">
                  <d:response>
                    <d:href>/Van Helsing.mkv</d:href>
                    <d:propstat><d:prop>
                      <d:getcontentlength>100</d:getcontentlength>
                      <d:getlastmodified>Tue, 09 Jun 2026 20:00:44 GMT</d:getlastmodified>
                    </d:prop></d:propstat>
                  </d:response>
                  <d:response>
                    <d:href>/Bridgerton.S02/Bridgerton.S02E01.mkv</d:href>
                    <d:propstat><d:prop>
                      <d:getcontentlength>200</d:getcontentlength>
                      <d:getlastmodified>Tue, 10 Jun 2026 20:00:44 GMT</d:getlastmodified>
                    </d:prop></d:propstat>
                  </d:response>
                  <d:response>
                    <d:href>/Bridgerton.S02/Bridgerton.S02E02.mkv</d:href>
                    <d:propstat><d:prop>
                      <d:getcontentlength>300</d:getcontentlength>
                      <d:getlastmodified>Tue, 11 Jun 2026 20:00:44 GMT</d:getlastmodified>
                    </d:prop></d:propstat>
                  </d:response>
                </d:multistatus>
                """;
        List<HomeMediaLibraryFile> files = service.parseFiles(xml);

        List<HomeMediaLibraryItem> items = service.groupFiles(files);

        assertThat(items).hasSize(2);
        HomeMediaLibraryItem folder = items.stream()
                .filter(HomeMediaLibraryItem::isFolder)
                .findFirst()
                .orElseThrow();
        HomeMediaLibraryItem directFile = items.stream()
                .filter(HomeMediaLibraryItem::isDirectFile)
                .findFirst()
                .orElseThrow();
        assertThat(folder.displayName()).isEqualTo("Bridgerton.S02");
        assertThat(folder.folderPath()).isEqualTo("Bridgerton.S02");
        assertThat(folder.fileCount()).isEqualTo(2);
        assertThat(folder.totalSizeBytes()).isEqualTo(500);
        assertThat(folder.folderKey()).hasSize(16);
        assertThat(directFile.displayName()).isEqualTo("Van Helsing.mkv");
        assertThat(directFile.file()).isNotNull();
        assertThat(directFile.fileCount()).isEqualTo(1);
        assertThat(directFile.totalSizeBytes()).isEqualTo(100);
    }

    @Test
    void shouldCreateStableFolderKeyForCyrillicFolderWithSpaces() {
        HomeWebdavMediaLibraryService service = service();

        String firstKey = service.folderKey("Бриджертоны.Bridgerton.S04.WEB-DLRip.Videofilm Int");
        String secondKey = service.folderKey("Бриджертоны.Bridgerton.S04.WEB-DLRip.Videofilm Int");

        assertThat(firstKey).isEqualTo(secondKey);
        assertThat(firstKey).hasSize(16);
        assertThat(firstKey).doesNotContain("/", "+", "=");
    }

    private HomeWebdavMediaLibraryService service() {
        return new HomeWebdavMediaLibraryService(
                new HomeWebdavProperties(
                        true,
                        "http://100.123.82.79:8085/",
                        "http://192.168.1.189:8085/",
                        "infuse",
                        "password",
                        1000,
                        1000
                ),
                WebClient.builder()
        );
    }
}
