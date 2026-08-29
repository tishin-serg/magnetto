# Telegram Torrent Bot MVP

Бот принимает magnet-ссылку или название фильма, ищет раздачи через JacRed, добавляет выбранный torrent в qBittorrent, отслеживает прогресс и доставляет результат пользователю.

## Что умеет

- `/start` и `/help` показывают меню с кнопками.
- `/library` показывает файлы, доступные в WebDAV-медиатеке.
- Magnet-ссылка запускает загрузку напрямую.
- Обычный текст или `/search название` запускает поиск через JacRed.
- Результаты поиска показываются страницами с сидами, пирами, размером, качеством, трекером, датой и ссылкой на страницу раздачи.
- Если в torrent несколько видеофайлов, бот спрашивает, что именно скачивать, до основной загрузки.
- Статус загрузки обновляется редактированием старого сообщения примерно каждые 5 секунд.
- В статусе видны процент, скорость, остаток, примерное время и свободное место.
- Файлы до `TELEGRAM_DIRECT_SEND_LIMIT_BYTES` отправляются в Telegram через `sendDocument`.
- Большие файлы попадают в WebDAV-медиатеку для Infuse на iPhone и получают временную fallback-ссылку.
- Есть кнопка принудительной очистки медиатеки.
- Новые задачи ставятся в очередь, если уже идёт активная загрузка/доставка.

## Архитектура

- **bot-app**: Java 21 + Spring Boot 3, Telegram polling, orchestration, retry, PostgreSQL state.
- **qBittorrent**: скачивает torrents в общий volume `/downloads`.
- **JacRed**: torrent indexer, совместимый с Jackett API.
- **PostgreSQL**: хранит jobs, files, download links и Telegram polling offset.
- **telegram-bot-api**: self-hosted Bot API server в local mode.
- **nginx**: отдаёт fallback-ссылки через `X-Accel-Redirect`.
- **WebDAV**: rclone WebDAV отдаёт `/downloads/media-library` для Infuse.

## JacRed

Поиск идёт через:

```text
GET /api/v2.0/indexers/all/results
```

Настройки:

```env
JACRED_BASE_URL=http://172.17.0.1:9117
JACRED_API_KEY=...
JACRED_MAX_RESULTS=5
```

Если бот запущен не в Docker или использует host network:

```env
JACRED_BASE_URL=http://127.0.0.1:9117
```

Логика поиска:

- сначала запрос по распознанному названию/году/типу;
- если результатов мало, fallback через общий `query`;
- выше ставятся раздачи с `MagnetUri`, большим числом сидов и нормальным качеством;
- CAMRip/TS/TeleSync/экранки скрываются, если есть нормальные варианты;
- постеры JacRed/Jackett API обычно не отдаёт, для них нужна отдельная интеграция с TMDb/Kinopoisk.

## Выбор Файлов

После появления metadata бот получает список файлов torrent через qBittorrent API. Если видеофайлов несколько:

- бот ставит torrent на паузу;
- выставляет приоритет `0` всем файлам;
- показывает кнопки выбора;
- после выбора выставляет приоритет `1` выбранным файлам и продолжает загрузку.

Так можно скачать одну серию или одну часть, а не весь torrent целиком.

## iPhone И Большие Фильмы

Telegram Bot API безопасно используется только до:

```env
TELEGRAM_DIRECT_SEND_LIMIT_BYTES=2040109465
```

Для больших фильмов бот использует WebDAV. В текущей compose-схеме медиатека лежит внутри downloads volume:

```env
MEDIA_LIBRARY_PATH=/downloads/media-library
```

Это позволяет использовать hardlink и не удваивать место на диске для новых файлов.

В Infuse:

1. Установить Infuse.
2. Открыть `Add Files / Shares`.
3. Выбрать WebDAV.
4. Указать URL из `MEDIA_LIBRARY_PUBLIC_WEBDAV_URL`.
5. Ввести `WEBDAV_USERNAME` и `WEBDAV_PASSWORD`.
6. После загрузки фильма ботом открыть папку и нажать Download.
7. Перед поездкой проверить, что фильм скачан офлайн.

## Зачем Нужна Медиатека

qBittorrent downloads нужны для скачивания, сидирования и cleanup. WebDAV-медиатека нужна как стабильная папка для Infuse. Раньше она была отдельным Docker volume, поэтому большие фильмы могли копироваться и занимать место дважды. Теперь новые фильмы кладутся в `/downloads/media-library`, где hardlink работает в том же volume.

## Очистка

В меню есть кнопка `Очистить медиатеку`. Она:

- требует подтверждения;
- удаляет только файлы внутри `MEDIA_LIBRARY_PATH`;
- не трогает активные torrents напрямую;
- освобождает место для новых загрузок.

## Запуск

Создать `.env` из `.env.example` и заполнить секреты:

```env
TELEGRAM_BOT_TOKEN=
TELEGRAM_BOT_USERNAME=
TELEGRAM_API_ID=
TELEGRAM_API_HASH=
QBITTORRENT_USERNAME=admin
QBITTORRENT_PASSWORD=adminadmin
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/torrentbot
SPRING_DATASOURCE_USERNAME=torrentbot
SPRING_DATASOURCE_PASSWORD=torrentbot
JACRED_API_KEY=
PROGRESS_POLL_INTERVAL_MS=5000
```

Запуск:

```bash
docker compose --profile local-api up --build -d
```

Логи:

```bash
docker compose logs -f bot-app
docker compose logs -f qbittorrent
docker compose logs -f telegram-bot-api
docker compose logs -f nginx
docker compose logs -f webdav
```

## Ограничения MVP

- `.torrent` uploads не являются главным сценарием.
- FFmpeg, userbot/MTProto, Redis, quotas и web UI не используются.
- Постеры в поиске не показываются, потому что JacRed/Jackett API обычно не отдаёт poster URL.
- Временный результат поиска хранится в памяти около 30 минут; после рестарта нужно повторить поиск.
## Скачивание сразу на домашний ПК

Бот умеет выбирать, куда отправлять torrent:

- `VPS` — старое поведение: qBittorrent в Docker скачивает в `/downloads`.
- `Домашний ПК` — бот с VPS управляет qBittorrent на домашнем компьютере через Tailscale, а файл сразу пишется на домашний диск.

Для домашнего сценария на ПК нужно поднять qBittorrent Web UI/API и WebDAV-папку для Infuse, например через `rclone serve webdav`. Tailscale должен быть установлен на домашнем ПК, VPS и iPhone.

Пример переменных:

```env
QBITTORRENT_HOME_BASE_URL=http://100.x.y.z:8080
QBITTORRENT_HOME_USERNAME=admin
QBITTORRENT_HOME_PASSWORD=strong-password
QBITTORRENT_HOME_DOWNLOAD_PATH=/media/movies

HOME_WEBDAV_ENABLED=true
HOME_WEBDAV_BASE_URL=http://100.x.y.z:8085/
HOME_WEBDAV_USERNAME=infuse
HOME_WEBDAV_PASSWORD=strong-password
```

Если домашний ПК выключен или qBittorrent недоступен, задача не отменяется: бот поставит ее в retry и продолжит попытки автоматически.

S3 для torrent напрямую не используется: qBittorrent плохо работает с object storage как с обычным диском. Практичный вариант для S3 — скачать на VPS или домашний ПК, затем загрузить готовый файл в S3 и удалить локальную копию.

## Home WebDAV Links

For downloads targeted to the home PC, the bot can show both WebDAV addresses:

```env
HOME_WEBDAV_ENABLED=true
HOME_WEBDAV_BASE_URL=http://100.x.y.z:8085/
HOME_WEBDAV_LOCAL_BASE_URL=http://192.168.1.189:8085/
HOME_WEBDAV_USERNAME=infuse
HOME_WEBDAV_PASSWORD=strong-password
```

- `HOME_WEBDAV_BASE_URL` is the Tailscale address, useful for iPhone outside the home network.
- `HOME_WEBDAV_LOCAL_BASE_URL` is the home Wi-Fi/LAN address, useful for a TV at home.
- The home PC should run WebDAV on `0.0.0.0:8085`, not only on `127.0.0.1`.
- Windows Firewall should allow inbound TCP `8085` on the private home network.
- It is best to reserve the home PC LAN IP in the router, otherwise the TV URL can change after reboot.

After a home PC download finishes, the bot edits the status message and adds buttons for the Tailscale file link, the Wi-Fi file link, and the WebDAV folder. The `Медиатека` button shows both VPS media files and home WebDAV files when home WebDAV is enabled.

## Movie Search UX

The bot can search movies and series in two steps:

1. TMDb is used for a friendly movie card: title, original title, year, rating and poster.
2. JacRed is used for torrent releases after the user chooses a movie card.

Required variables:

```env
TMDB_API_KEY=
TMDB_LANGUAGE=ru-RU
TMDB_CACHE_TTL_MINUTES=360
TMDB_MAX_INLINE_RESULTS=10
```

For Findvid-like search inside Telegram input, enable inline mode for the bot in BotFather:

```text
/setinline
@magnet_bott
```

After that, typing `@magnet_bott матрица 1999` in Telegram should show movie cards with posters. Selecting a card posts it to the chat; pressing "Найти раздачи" opens JacRed torrent results.

Chat search still works too: send `/search матрица 1999` or just type a movie name.

## Observability

The bot logs search stages and records Micrometer metrics for future optimization:

- TMDb search latency and cache hit/miss.
- JacRed search latency and failures.
- Inline query handling latency.
- Telegram inline answer latency.

Actuator endpoints:

```text
/actuator/health
/actuator/metrics
```

Do not log bot tokens, TMDb/JacRed keys or full private URLs. Search logs use short previews and hashes where useful.
