package ru.xataaa.torrentbot.downloadlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DownloadControllerTest {

    private final DownloadLinkService downloadLinkService = mock(DownloadLinkService.class);
    private final DownloadController downloadController = new DownloadController(downloadLinkService);

    @Test
    void shouldReturnXAccelRedirectForValidToken() {
        DownloadLink downloadLink = DownloadLink.builder()
                .id(UUID.randomUUID())
                .token("token")
                .originalFileName("movie.mkv")
                .storedFileName("stored.mkv")
                .fileSizeBytes(100)
                .status(DownloadLinkStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .createdAt(LocalDateTime.now())
                .build();
        when(downloadLinkService.getValidDownloadLink("token")).thenReturn(Optional.of(downloadLink));
        when(downloadLinkService.xAccelRedirectPath(downloadLink)).thenReturn("/protected-downloads/stored.mkv");

        ResponseEntity<Void> response = downloadController.download("token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Accel-Redirect")).isEqualTo("/protected-downloads/stored.mkv");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(100);
    }

    @Test
    void shouldReturnNotFoundForUnknownOrExpiredToken() {
        when(downloadLinkService.getValidDownloadLink("missing")).thenReturn(Optional.empty());

        ResponseEntity<Void> response = downloadController.download("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
