package ru.xataaa.torrentbot.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DownloadTargetTest {

    @Test
    void shouldUseS3AsPrimaryDatabaseValueAndKeepLegacyS3LaterReadable() {
        assertThat(DownloadTarget.fromValue("S3")).isEqualTo(DownloadTarget.S3);
        assertThat(DownloadTarget.fromValue("s3")).isEqualTo(DownloadTarget.S3);
        assertThat(DownloadTarget.fromValue("S3_LATER")).isEqualTo(DownloadTarget.S3_LATER);
        assertThat(DownloadTarget.S3.isS3()).isTrue();
        assertThat(DownloadTarget.S3_LATER.isS3()).isTrue();
    }
}
