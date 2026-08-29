package ru.xataaa.torrentbot.downloadlink;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DownloadController {

    private static final String X_ACCEL_REDIRECT = "X-Accel-Redirect";

    private final DownloadLinkService downloadLinkService;

    @GetMapping("/download/{token}")
    public ResponseEntity<Void> download(@PathVariable String token) {
        return downloadLinkService.getValidDownloadLink(token)
                .map(this::buildDownloadResponse)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private ResponseEntity<Void> buildDownloadResponse(DownloadLink downloadLink) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(X_ACCEL_REDIRECT, downloadLinkService.xAccelRedirectPath(downloadLink));
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(downloadLink.getFileSizeBytes());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(downloadLink.getOriginalFileName(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).build();
    }
}
