package ru.xataaa.torrentbot.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.xataaa.torrentbot.application.*;
import ru.xataaa.torrentbot.config.ShortcutApiProperties;
import ru.xataaa.torrentbot.file.DownloadFile;
import ru.xataaa.torrentbot.job.*;
import ru.xataaa.torrentbot.movie.MovieMetadata;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchResult;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "shortcut-api", name = "enabled", havingValue = "true")
public class ShortcutApiController {
    private final ShortcutApiProperties properties;
    private final DownloadApplicationService application;
    private final DownloadQueryService query;
    private final DownloadControlService control;
    private final ShortcutPreferencesRepository preferencesRepository;
    private final ApiIdempotencyRepository idempotency;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @PostMapping("/catalog/search")
    public Mono<List<CatalogItem>> catalog(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth, @RequestBody CatalogRequest request) {
        authenticate(auth); return blocking(() -> application.searchCatalog(request.query()).stream().map(CatalogItem::from).toList());
    }
    @PostMapping("/torrents/search")
    public Mono<List<TorrentItem>> torrents(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth, @RequestBody TorrentRequest request) {
        authenticate(auth); return blocking(() -> application.searchTorrents(request.selectionId(), request.season(), safe(request.episodes()), request.quality(), request.voice()).stream().map(TorrentItem::from).toList());
    }
    @PostMapping("/downloads")
    public Mono<ResponseEntity<?>> create(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth, @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody CreateRequest request) {
        if (key == null || key.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
        authenticate(auth); checkCreationRate();
        return blocking(() -> createDownload(key, request));
    }
    private ResponseEntity<?> createDownload(String key, CreateRequest request) {
        UUID userId = apiUserId();
        UUID known = idempotency.find(userId, key).orElse(null); if (known != null) return ResponseEntity.accepted().body(new JobAccepted(known));
        DeliveryTarget target = request.deliveryTarget() == null ? DeliveryTarget.PHONE_VPS_TEMP : request.deliveryTarget();
        UUID id = application.create(userId, properties.telegramChatId() == null ? 0L : properties.telegramChatId(), request.movieSelectionId(), request.torrentSelectionId(), request.season(), safe(request.episodes()), request.quality(), request.voice(), target, request.automatic(), preferencesRepository.find(userId));
        if (!idempotency.saveIfAbsent(userId, key, id)) {
            return ResponseEntity.accepted().body(new JobAccepted(idempotency.find(userId, key).orElse(id)));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new JobAccepted(id));
    }
    @GetMapping("/downloads")
    public Mono<List<JobView>> list(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) { authenticate(auth); return blocking(() -> query.recent(chat(), 50).stream().map(j -> view(j, query.files(j.getId()), query.readyLinks(j))).toList()); }
    @GetMapping("/downloads/{id}")
    public Mono<ResponseEntity<?>> get(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth, @PathVariable UUID id) { authenticate(auth); return blocking(() -> { DownloadJob j = query.owned(id, chat()); return j == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view(j, query.files(id), query.readyLinks(j))); }); }
    @PostMapping("/downloads/{id}/pause") public Mono<ResponseEntity<?>> pause(@RequestHeader(value=HttpHeaders.AUTHORIZATION,required=false) String a,@PathVariable UUID id){ authenticate(a); return blocking(() -> { DownloadJob j=owned(id); if(j==null)return ResponseEntity.notFound().build(); control.pause(j); return ResponseEntity.accepted().build(); }); }
    @PostMapping("/downloads/{id}/resume") public Mono<ResponseEntity<?>> resume(@RequestHeader(value=HttpHeaders.AUTHORIZATION,required=false) String a,@PathVariable UUID id){ authenticate(a); return blocking(() -> { DownloadJob j=owned(id); if(j==null)return ResponseEntity.notFound().build(); control.resume(j); return ResponseEntity.accepted().build(); }); }
    @PutMapping("/downloads/{id}/files") public Mono<ResponseEntity<?>> files(@RequestHeader(value=HttpHeaders.AUTHORIZATION,required=false) String a,@PathVariable UUID id,@RequestBody FileSelection body){ authenticate(a); return blocking(() -> { DownloadJob j=owned(id); if(j==null)return ResponseEntity.notFound().build(); control.select(j,body.fileIds()); return ResponseEntity.accepted().build(); }); }
    @GetMapping("/preferences") public Mono<ShortcutPreferences> preferences(@RequestHeader(value=HttpHeaders.AUTHORIZATION,required=false) String a){ authenticate(a); return blocking(() -> preferencesRepository.find(apiUserId())); }
    @PutMapping("/preferences") public Mono<ShortcutPreferences> savePreferences(@RequestHeader(value=HttpHeaders.AUTHORIZATION,required=false) String a,@RequestBody ShortcutPreferences p){ authenticate(a); return blocking(() -> { preferencesRepository.save(apiUserId(),p); return p; }); }

    private <T> Mono<T> blocking(Supplier<T> supplier){ return Mono.fromCallable(supplier::get).subscribeOn(Schedulers.boundedElastic()); }
    private UUID apiUserId(){ return preferencesRepository.ensureUser(properties.userId(), properties.telegramChatId()); }
    private DownloadJob owned(UUID id){ return query.owned(id,chat()); }
    private Long chat(){ return properties.telegramChatId()==null?0L:properties.telegramChatId(); }
    private void authenticate(String value){ if(value==null||!value.startsWith("Bearer ")||properties.tokenSha256()==null) throw new ApiException(HttpStatus.UNAUTHORIZED,"Unauthorized"); String got=sha256(value.substring(7).trim()), expected=properties.tokenSha256().trim().toLowerCase(Locale.ROOT); if(!MessageDigest.isEqual(got.getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII))) throw new ApiException(HttpStatus.UNAUTHORIZED,"Unauthorized"); checkRate(); }
    private void checkRate(){ limit("requests",properties.requestsPerMinute()); }
    private void checkCreationRate(){ limit("creations",properties.creationsPerMinute()); }
    private void limit(String kind,int max){ String k=properties.userId()+":"+kind; Window w=windows.compute(k,(x,old)->old==null||old.started.plusSeconds(60).isBefore(Instant.now())?new Window(Instant.now(),1):new Window(old.started,old.count+1)); if(w.count>max)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Rate limit exceeded"); }
    private String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private JobView view(DownloadJob j,List<DownloadFile> fs,List<String> links){return new JobView(j.getId(),j.getStatus().name(),j.getLastReportedProgressPercent(),j.getErrorMessage(),links,fs.stream().map(f->new FileView(f.getId(),f.getFileName(),f.getSizeBytes(),f.getStatus().name())).toList());}
    private static Set<Integer> safe(Set<Integer> v){return v==null?Set.of():v;}
    private record Window(Instant started,int count){}
    public record CatalogRequest(String query){} public record TorrentRequest(String selectionId,Integer season,Set<Integer> episodes,String quality,String voice){}
    public record CreateRequest(String movieSelectionId,String torrentSelectionId,Integer season,Set<Integer> episodes,String quality,String voice,DeliveryTarget deliveryTarget,boolean automatic){}
    public record FileSelection(List<UUID> fileIds){}
    public record JobAccepted(UUID jobId){}
    public record CatalogItem(String selectionId,String tmdbId,String type,String title,Integer year,Double rating){static CatalogItem from(MovieMetadata m){return new CatalogItem(m.selectionId(),m.tmdbId(),m.mediaType().name(),m.title(),m.year(),m.rating());}}
    public record TorrentItem(String selectionId,String title,long sizeBytes,int seeders){static TorrentItem from(TorrentSearchResult r){return new TorrentItem(r.selectionId(),r.title(),r.sizeBytes(),r.seeders());}}
    public record JobView(UUID jobId,String status,int progress,String error,List<String> links,List<FileView> files){} public record FileView(UUID fileId,String name,long sizeBytes,String status){}
    public static class ApiException extends RuntimeException { final HttpStatus status; ApiException(HttpStatus s,String m){super(m);status=s;} }
}
