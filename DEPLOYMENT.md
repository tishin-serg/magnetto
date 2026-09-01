# Magnettto Bot: Project And Deploy Guide

## Project

`magnettto_bot` is a Java 21 / Spring Boot Telegram bot for finding torrents, choosing exact files inside a torrent, and sending downloads to VPS or a home PC.

Main production path on VDS:

```bash
/opt/torrentbot
```

Main services in `docker-compose.yml`:

- `bot-app`: Spring Boot Telegram bot.
- `postgres`: persistent bot state, jobs, file links, Telegram polling offset.
- `qbittorrent`: VPS torrent downloader.
- `nginx`: protected HTTP delivery for files.
- `webdav`: media library access for Infuse/TV.
- `telegram-bot-api`: optional local Telegram Bot API server, enabled by `local-api` profile.

Important source directories:

```text
src/main/java/ru/xataaa/torrentbot/telegram       Telegram commands, callbacks, keyboards
src/main/java/ru/xataaa/torrentbot/movie          TMDb search and movie/series sessions
src/main/java/ru/xataaa/torrentbot/torrentsearch  Jacred search, torrent parsing, filters
src/main/java/ru/xataaa/torrentbot/qbittorrent    qBittorrent API integration
src/main/java/ru/xataaa/torrentbot/job            Download orchestration and retries
src/main/resources/db/changelog                   Liquibase migrations
```

Important files:

```text
pom.xml
docker-compose.yml
Dockerfile
Dockerfile.runtime
.env.example
```

Production secrets live in `/opt/torrentbot/.env`. Do not overwrite `.env` during source sync.

## Search Flow

Current movie/series UX:

1. Inline or chat query goes to TMDb.
2. User selects the correct title.
3. Bot builds torrent availability through Jacred.
4. User chooses season, episode/range, voice and quality.
5. Bot shows matching releases.
6. If a torrent contains multiple video files, bot shows file selection before download.
7. Bot confirms the exact selected season/episode before target selection.
8. User chooses VPS or home PC target.

Voice filtering is token-based:

- `LostFilm` matches any release whose parsed voice contains LostFilm.
- Composite voices like `Dub + Subtitles + LostFilm` are included when `LostFilm` is selected.
- Voice buttons are aggregated; subtitles/original markers should not pollute the main list.

Quality filtering is grouped for users:

- Any quality
- Optimal
- Smaller size
- Maximum quality
- Advanced technical choices

## Useful Logs

Bot app logs:

```bash
cd /opt/torrentbot
docker compose logs -f bot-app
```

Recent bot logs:

```bash
docker logs --since=10m torrentbot-bot-app-1 2>&1 | tail -200
```

Search latency logs to look for:

```text
tmdb_search_started
tmdb_search_completed
tmdb_cache_hit
tmdb_cache_miss
tmdb_singleflight_wait
jacred_client_search_started
jacred_client_search_completed
jacred_search_completed
availability_catalog_cache_hit
availability_catalog_cache_miss
availability_catalog_singleflight_wait
```

Health check:

```bash
docker exec torrentbot-bot-app-1 sh -c 'wget -qO- http://127.0.0.1:8080/actuator/health'
```

Expected response:

```json
{"status":"UP"}
```

## Source Sync To VDS

Recommended Windows deploy path:

```powershell
cd C:\Users\xataa\Documents\скачиватель
.\scripts\deploy-vdska-pl.ps1
```

The script runs local tests with `.\mvnw.cmd test`, builds the local jar with JDK 21, syncs source to `vdska_pl`, then builds and restarts `bot-app` on the server. Use `-SkipTests` only when you intentionally want to skip the local test gate.

From Windows project directory:

```powershell
cd C:\Users\xataa\Documents\скачиватель

$archive = Join-Path $env:TEMP 'torrentbot-source.tar.gz'
if (Test-Path $archive) { Remove-Item -LiteralPath $archive -Force }

tar -czf $archive `
  --exclude='./target' `
  --exclude='./.git' `
  --exclude='./.agents' `
  --exclude='./.codex' `
  --exclude='./.env' `
  --exclude='./torrentbot-deploy.tar.gz' `
  .

scp $archive vdska_pl:/tmp/torrentbot-source.tar.gz
ssh vdska_pl "mkdir -p /opt/torrentbot && tar -xzf /tmp/torrentbot-source.tar.gz -C /opt/torrentbot"
```

This keeps `/opt/torrentbot/.env` untouched.

## Build Jar On VDS

Build production jar from `/opt/torrentbot`.

Preferred command:

```bash
cd /opt/torrentbot
docker run --rm \
  -v /opt/torrentbot:/workspace \
  -w /workspace \
  maven:3.9-eclipse-temurin-21 \
  mvn -B -Dmaven.test.skip=true package
```

The jar should appear here:

```text
/opt/torrentbot/target/torrentbot-0.0.1-SNAPSHOT.jar
```

Check:

```bash
ls -lh /opt/torrentbot/target/torrentbot-0.0.1-SNAPSHOT.jar
```

## Fast Production Deploy

Use the already built jar and `Dockerfile.runtime`. This avoids slow Maven work inside `docker compose build`.

```bash
cd /opt/torrentbot

docker build \
  -f Dockerfile.runtime \
  -t torrentbot-bot-app .

docker compose up -d --no-deps bot-app
```

Verify:

```bash
docker compose ps bot-app
docker logs --since=2m torrentbot-bot-app-1 2>&1 | tail -200
docker exec torrentbot-bot-app-1 sh -c 'wget -qO- http://127.0.0.1:8080/actuator/health'
```

Expected:

- container status is `Up`;
- Spring Boot logs contain `Started TorrentBotApplication`;
- health returns `{"status":"UP"}`.

## Full Compose Deploy

Use this only when infrastructure files changed and a slower full build is acceptable:

```bash
cd /opt/torrentbot
docker compose up -d --build
```

For normal bot code changes, prefer the fast production deploy above.

## Tests

Run focused tests locally if Maven/JDK 21 is available:

```bash
mvn -Dtest=TorrentAvailabilityCatalogTest,MovieMetadataServiceTest test
```

On VDS:

```bash
cd /opt/torrentbot
docker run --rm \
  -v /opt/torrentbot:/workspace \
  -w /workspace \
  maven:3.9-eclipse-temurin-21 \
  mvn -Dtest=TorrentAvailabilityCatalogTest,MovieMetadataServiceTest test
```

If VDS is under load, skip tests during deploy and run focused tests later. Do not leave stuck Maven/Docker processes running.

Check for stuck build processes:

```bash
ps -eo pid,comm,args | grep -E 'mvn|maven:3.9|docker compose build|buildkit/executor' | grep -v grep
```

Stop only processes that were started by the current deploy session:

```bash
kill <pid>
kill -9 <pid>
```

## Safe Deploy Checklist

1. Do not overwrite `/opt/torrentbot/.env`.
2. Sync source without `target/`, `.git`, `.env`.
3. Build jar successfully.
4. Build runtime image with `Dockerfile.runtime`.
5. Restart only `bot-app` with `--no-deps`.
6. Check `docker compose ps bot-app`.
7. Check logs for startup errors.
8. Check `/actuator/health`.
9. After user testing, inspect new search logs for TMDb/Jacred timings.

## Rollback

If the new container fails after deploy:

```bash
cd /opt/torrentbot
docker images torrentbot-bot-app
docker compose logs --tail=200 bot-app
```

If a previous image is still available, retag it manually and restart `bot-app`. If not, restore source from `/opt/torrentbot-backups` and rebuild:

```bash
ls -la /opt/torrentbot-backups
```

Then copy the needed backup into `/opt/torrentbot`, rebuild the jar, rebuild runtime image, and restart `bot-app`.

## Current Operational Notes

- Jacred health can be instant while searches are slow; slow search usually means Jacred/indexer fan-out, not bot-to-Jacred network.
- TMDb and availability catalog use in-memory cache and singleflight. Restart clears memory caches.
- Persistent Postgres cache for TMDb/Jacred metadata is not implemented yet.
- The production compose uses `build: .`, which points to the slow multi-stage `Dockerfile`; for routine deploys use `Dockerfile.runtime` manually.
