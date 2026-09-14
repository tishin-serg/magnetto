package ru.xataaa.torrentbot.api;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ru.xataaa.torrentbot.application.DownloadApplicationService;

@RestControllerAdvice
public class ShortcutApiExceptionHandler {
    @ExceptionHandler(ShortcutApiController.ApiException.class)
    ResponseEntity<ProblemDetail> api(ShortcutApiController.ApiException e){ ProblemDetail p=ProblemDetail.forStatusAndDetail(e.status,e.getMessage()); return ResponseEntity.status(e.status).contentType(MediaType.valueOf("application/problem+json")).body(p); }
    @ExceptionHandler(DownloadApplicationService.SelectionExpiredException.class)
    ResponseEntity<ProblemDetail> expired(Exception e){return problem(HttpStatus.GONE,e.getMessage());}
    @ExceptionHandler(DownloadApplicationService.NoAutomaticTorrentException.class)
    ResponseEntity<ProblemDetail> noTorrent(Exception e){return problem(HttpStatus.UNPROCESSABLE_ENTITY,e.getMessage());}
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid(IllegalArgumentException e){return problem(HttpStatus.BAD_REQUEST,e.getMessage());}
    private ResponseEntity<ProblemDetail> problem(HttpStatus s,String d){return ResponseEntity.status(s).contentType(MediaType.valueOf("application/problem+json")).body(ProblemDetail.forStatusAndDetail(s,d));}
}
