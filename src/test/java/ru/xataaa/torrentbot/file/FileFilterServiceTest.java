package ru.xataaa.torrentbot.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileFilterServiceTest {

    private final FileFilterService fileFilterService = new FileFilterService();

    @Test
    void shouldAllowSupportedVideo() {
        assertThat(fileFilterService.classify("movie.mkv", 999)).isEqualTo(DownloadFileStatus.READY_TO_UPLOAD);
        assertThat(fileFilterService.classify("movie.MP4", 999)).isEqualTo(DownloadFileStatus.READY_TO_UPLOAD);
    }

    @Test
    void shouldSkipUnsupportedFiles() {
        assertThat(fileFilterService.classify("readme.txt", 10)).isEqualTo(DownloadFileStatus.SKIPPED_UNSUPPORTED);
        assertThat(fileFilterService.classify("cover.jpg", 10)).isEqualTo(DownloadFileStatus.SKIPPED_UNSUPPORTED);
        assertThat(fileFilterService.classify("run.exe", 10)).isEqualTo(DownloadFileStatus.SKIPPED_UNSUPPORTED);
    }

    @Test
    void shouldSkipZeroFileAndKeepLargeVideoForLinkDelivery() {
        assertThat(fileFilterService.classify("empty.mp4", 0)).isEqualTo(DownloadFileStatus.SKIPPED_UNSUPPORTED);
        assertThat(fileFilterService.classify("huge.mp4", 1001)).isEqualTo(DownloadFileStatus.READY_TO_UPLOAD);
    }
}
