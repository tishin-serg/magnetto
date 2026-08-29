package ru.xataaa.torrentbot.movie;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.config.SearchProperties;
import ru.xataaa.torrentbot.config.TmdbProperties;
import ru.xataaa.torrentbot.torrentsearch.TorrentQuality;
import ru.xataaa.torrentbot.torrentsearch.VoiceFilter;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieSearchSessionService {

    private final SearchProperties searchProperties;
    private final TmdbProperties tmdbProperties;
    private final TmdbClient tmdbClient;
    private final Map<String, MovieSearchSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, CachedSeasons> seasonsCache = new ConcurrentHashMap<>();
    private final Map<String, CachedSeasonDetails> seasonDetailsCache = new ConcurrentHashMap<>();

    public MovieSearchSession create(MovieMetadata movieMetadata) {
        cleanupExpiredSessions();
        TorrentQuality defaultQuality = TorrentQuality.fromCode(searchProperties.defaultQuality());
        VoiceFilter defaultVoice = VoiceFilter.fromCode(searchProperties.defaultVoice());
        MovieSearchSession session = new MovieSearchSession(
                nextSessionId(),
                movieMetadata,
                defaultQuality,
                defaultVoice,
                null,
                null,
                null,
                null,
                Set.of(),
                Instant.now().plusSeconds(Math.max(60L, searchProperties.sessionTtlMinutes() * 60L))
        );
        sessions.put(session.sessionId(), session);
        log.info("movie_search_session_created: searchSessionId={}, tmdbId={}, mediaType={}",
                session.sessionId(), movieMetadata.tmdbId(), movieMetadata.mediaType());
        return session;
    }

    public Optional<MovieSearchSession> find(String sessionId) {
        cleanupExpiredSessions();
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        MovieSearchSession session = sessions.get(sessionId);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public Optional<MovieSearchSession> updateQuality(String sessionId, TorrentQuality quality) {
        return update(sessionId, session -> new MovieSearchSession(
                session.sessionId(),
                session.movieMetadata(),
                quality,
                session.voice(),
                session.availabilityVoice(),
                quality == TorrentQuality.ANY ? session.availabilityQuality() : quality.displayName(),
                session.availabilityScope(),
                session.seasonNumber(),
                session.episodeNumbers(),
                refreshedExpiresAt()
        ));
    }

    public Optional<MovieSearchSession> updateVoice(String sessionId, VoiceFilter voice) {
        return update(sessionId, session -> new MovieSearchSession(
                session.sessionId(),
                session.movieMetadata(),
                session.quality(),
                voice,
                voice == VoiceFilter.ANY ? session.availabilityVoice() : voice.displayName(),
                session.availabilityQuality(),
                session.availabilityScope(),
                session.seasonNumber(),
                session.episodeNumbers(),
                refreshedExpiresAt()
        ));
    }

    public Optional<MovieSearchSession> selectSeason(String sessionId, int seasonNumber) {
        return update(sessionId, session -> new MovieSearchSession(
                session.sessionId(),
                session.movieMetadata(),
                session.quality(),
                session.voice(),
                null,
                null,
                null,
                seasonNumber,
                Set.of(),
                refreshedExpiresAt()
        ));
    }

    public Optional<MovieSearchSession> toggleEpisode(String sessionId, int episodeNumber) {
        return update(sessionId, session -> {
            Set<Integer> episodeNumbers = new HashSet<>(session.episodeNumbers());
            if (episodeNumbers.contains(episodeNumber)) {
                episodeNumbers.remove(episodeNumber);
            } else {
                episodeNumbers.add(episodeNumber);
            }
            return new MovieSearchSession(
                    session.sessionId(),
                    session.movieMetadata(),
                    session.quality(),
                    session.voice(),
                    session.availabilityVoice(),
                    session.availabilityQuality(),
                    session.availabilityScope(),
                    session.seasonNumber(),
                    Set.copyOf(episodeNumbers),
                    refreshedExpiresAt()
            );
        });
    }

    public Optional<MovieSearchSession> selectAllEpisodes(String sessionId) {
        Optional<MovieSearchSession> optionalSession = find(sessionId);
        if (optionalSession.isEmpty() || optionalSession.get().seasonNumber() == null) {
            return optionalSession;
        }
        MovieSearchSession session = optionalSession.get();
        List<TvEpisodeSummary> episodes = episodes(session).episodes();
        Set<Integer> episodeNumbers = new HashSet<>();
        for (TvEpisodeSummary episode : episodes) {
            episodeNumbers.add(episode.episodeNumber());
        }
        MovieSearchSession updatedSession = new MovieSearchSession(
                session.sessionId(),
                session.movieMetadata(),
                session.quality(),
                session.voice(),
                session.availabilityVoice(),
                session.availabilityQuality(),
                session.availabilityScope(),
                session.seasonNumber(),
                Set.copyOf(episodeNumbers),
                refreshedExpiresAt()
        );
        sessions.put(sessionId, updatedSession);
        return Optional.of(updatedSession);
    }

    public Optional<MovieSearchSession> updateAvailabilityVoice(String sessionId, String voice) {
        return update(sessionId, session -> new MovieSearchSession(
                session.sessionId(),
                session.movieMetadata(),
                session.quality(),
                session.voice(),
                voice == null || voice.isBlank() ? null : voice.trim(),
                session.availabilityQuality(),
                session.availabilityScope(),
                session.seasonNumber(),
                session.episodeNumbers(),
                refreshedExpiresAt()
        ));
    }

    public Optional<MovieSearchSession> updateAvailabilityScope(String sessionId, String scope) {
        return update(sessionId, session -> new MovieSearchSession(
                session.sessionId(),
                session.movieMetadata(),
                session.quality(),
                session.voice(),
                null,
                null,
                scope == null || scope.isBlank() ? null : scope.trim(),
                session.seasonNumber(),
                session.episodeNumbers(),
                refreshedExpiresAt()
        ));
    }

    public Optional<MovieSearchSession> updateAvailabilityQuality(String sessionId, String quality) {
        return update(sessionId, session -> new MovieSearchSession(
                session.sessionId(),
                session.movieMetadata(),
                session.quality(),
                session.voice(),
                session.availabilityVoice(),
                quality == null || quality.isBlank() ? null : quality.trim(),
                session.availabilityScope(),
                session.seasonNumber(),
                session.episodeNumbers(),
                refreshedExpiresAt()
        ));
    }

    public List<TvSeasonSummary> seasons(MovieSearchSession session) {
        if (!session.movieMetadata().isTv()) {
            return List.of();
        }
        String cacheKey = session.movieMetadata().tmdbId();
        CachedSeasons cachedSeasons = seasonsCache.get(cacheKey);
        if (cachedSeasons != null && cachedSeasons.expiresAt().isAfter(Instant.now())) {
            return cachedSeasons.seasons();
        }
        List<TvSeasonSummary> seasons = tmdbClient.tvSeasons(session.movieMetadata().tmdbId()).stream()
                .filter(season -> season.getSeasonNumber() != null && season.getSeasonNumber() > 0)
                .map(season -> new TvSeasonSummary(
                        season.getSeasonNumber(),
                        season.getName() == null ? "Сезон " + season.getSeasonNumber() : season.getName(),
                        season.getEpisodeCount() == null ? 0 : season.getEpisodeCount()
                ))
                .sorted(Comparator.comparingInt(TvSeasonSummary::seasonNumber))
                .toList();
        seasonsCache.put(cacheKey, new CachedSeasons(seasons, detailsExpiresAt()));
        return seasons;
    }

    public TvSeasonDetails episodes(MovieSearchSession session) {
        if (!session.movieMetadata().isTv() || session.seasonNumber() == null) {
            return new TvSeasonDetails(0, "", List.of());
        }
        String cacheKey = session.movieMetadata().tmdbId() + ":" + session.seasonNumber();
        CachedSeasonDetails cachedSeasonDetails = seasonDetailsCache.get(cacheKey);
        if (cachedSeasonDetails != null && cachedSeasonDetails.expiresAt().isAfter(Instant.now())) {
            return cachedSeasonDetails.seasonDetails();
        }
        TmdbSeasonResponse response = tmdbClient.tvSeason(session.movieMetadata().tmdbId(), session.seasonNumber());
        List<TvEpisodeSummary> episodes = response == null || response.getEpisodes() == null
                ? List.of()
                : response.getEpisodes().stream()
                .filter(episode -> episode.getEpisodeNumber() != null && episode.getEpisodeNumber() > 0)
                .map(episode -> new TvEpisodeSummary(
                        episode.getEpisodeNumber(),
                        episode.getName() == null ? "" : episode.getName()
                ))
                .sorted(Comparator.comparingInt(TvEpisodeSummary::episodeNumber))
                .toList();
        String name = response == null || response.getName() == null ? "Сезон " + session.seasonNumber() : response.getName();
        TvSeasonDetails seasonDetails = new TvSeasonDetails(session.seasonNumber(), name, episodes);
        seasonDetailsCache.put(cacheKey, new CachedSeasonDetails(seasonDetails, detailsExpiresAt()));
        return seasonDetails;
    }

    private Optional<MovieSearchSession> update(String sessionId, SessionUpdater updater) {
        Optional<MovieSearchSession> optionalSession = find(sessionId);
        if (optionalSession.isEmpty()) {
            return Optional.empty();
        }
        MovieSearchSession updatedSession = updater.update(optionalSession.get());
        sessions.put(sessionId, updatedSession);
        return Optional.of(updatedSession);
    }

    private Instant refreshedExpiresAt() {
        return Instant.now().plusSeconds(Math.max(60L, searchProperties.sessionTtlMinutes() * 60L));
    }

    private Instant detailsExpiresAt() {
        return Instant.now().plusSeconds(Math.max(60L, tmdbProperties.detailsCacheTtlMinutes() * 60L));
    }

    private void cleanupExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        seasonsCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        seasonDetailsCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String nextSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private interface SessionUpdater {
        MovieSearchSession update(MovieSearchSession session);
    }

    private record CachedSeasons(List<TvSeasonSummary> seasons, Instant expiresAt) {
    }

    private record CachedSeasonDetails(TvSeasonDetails seasonDetails, Instant expiresAt) {
    }
}
