# Telegram Torrent Bot MVP

Бот принимает magnet-ссылку или название фильма, ищет раздачи через JacRed, добавляет выбранный torrent в qBittorrent, отслеживает прогресс и доставляет результат пользователю.

## Работа с проектом

Правила веток, коммитов, проверки и выкладки: [AGENTS.md](AGENTS.md).
Правила номеров версий, тегов и выпуска: [VERSIONING.md](VERSIONING.md).

Новая самостоятельная задача получает ветку `MGB-NN`. Основная ветка — `master`.
Номер задачи и номер версии приложения ведутся независимо.

## История релизов и этапов

История ниже восстановлена по локальным и удалённым веткам и коммитам.
На 6 сентября 2026 года тегов и GitHub Releases нет; версия приложения
в `pom.xml` — `0.0.1-SNAPSHOT`. Поэтому старым этапам не присвоены
задним числом номера версий. Даты в таблице — даты коммитов, не подтверждённые
даты выкладки на сервер. Все перечисленные завершённые этапы входят в `master`.

| Этап | Даты коммитов | Что вошло | Контрольный коммит |
| --- | --- | --- | --- |
| База проекта | 30–31.08.2026 | Исходный Telegram-бот с маршрутизацией текстовых запросов через LLM и поддержкой S3; восстановление запуска домашнего WebDAV. | `3b1d4d1`, `a3e4b9d` |
| MGB-01 — обновления прогресса | 01.09.2026 | Ограничена частота обновления сообщений о ходе загрузки. | `b333409` |
| MGB-02 — сборка, выкладка и S3 | 01–02.09.2026 | Maven Wrapper и проверка сборки; GitHub Actions CI/CD; выкладка готового JAR, проверки готовности и перезапуск приложения; восстановление доставки в S3. | `7724e51` |
| MGB-03 — скорость ответов | 02.09.2026 | Асинхронная обработка сообщений, быстрые маршруты команд, кэширование поиска и снижение задержек ответа; упаковка JAR для CI и ручная выкладка выбранной ветки. | `d55c998` |
| MGB-04 — задержки поиска | 03.09.2026 | Прямой поиск раздач через /search, ограничения времени резервных запросов JacRed и TMDb, пропуск малоинформативного fallback и параллельный поиск. Слияние: `22f20e8`. | `2b6d55d` |
| MGB-05 — подсказки TMDb | 03–04.09.2026 | Использование кэша при inline-поиске, сокращение фоновых запросов, замена HTTP-клиента TMDb. Слияние: `9788534`. | `15f5434` |
| Доработки после MGB-05 | 04–05.09.2026 | Диагностика ошибок TMDb, IPv4, переход к curl, настройка памяти процесса, выдача подсказок при первом запросе и исправление повторной обработки inline-выбора. | `2bc9d34`, `05793ee` |

### MGB-06 — подбор фильма с подтверждением

Статус: **проверено на сервере 06.09.2026, не слито в master, без тега**.
Коммит реализации: `4ac1e77`. Ветка первоначально называлась
`feature/movie-download-preferences`, затем приведена к принятому формату `MGB-06`.

- Настройки размера «от — до», минимума сидов и места скачивания в Telegram.
- Автоматический подбор после выбора фильма в TMDb с обязательным подтверждением.
- Разовые условия заявки, переключение вариантов, ручной выбор и отмена.
- Повторная проверка размера по metadata и отдельное подтверждение при расхождении.
- Защита от повторного запуска и исключение явно обозначенных сборников из подбора.

Проверка: 110 автоматических тестов и сценарии в Telegram.
Подробности: [отчёт проверки](docs/confirmation-test-report.md).

Первый будущий релиз с номером планируется как `0.1.0`; он ещё не выпущен.

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

## Подбор фильма с подтверждением

После выбора фильма в подсказках TMDb бот подбирает раздачу по сохранённым
условиям и показывает размер, качество, озвучку, сидов и место скачивания.
Задача создаётся только кнопкой «Скачать». Для сериалов сохранён выбор сезонов/серий.

В личном чате /settings (или «Настройки скачивания» в меню) задаёт:
- размер от и до, включительно; ввод в десятичных ГБ (1 ГБ = 1 000 000 000 байт);
- минимум сидов, от 1;
- домашний ПК, VPS или S3 через VPS.

Начальные значения: 4–15 ГБ, 10 сидов, домашний ПК. Настройки хранятся в
PostgreSQL и сохраняются после каждого изменения. «Изменить условия» в карточке
меняет только текущую заявку. Старые кнопки перестают действовать после изменения
условий/варианта. Карточки и незавершённый ввод живут 30/10 минут и после рестарта
требуют повторного открытия; сохранённые настройки и ограничения созданных задач
переживают рестарт.

Подбор исключает неизвестный размер, раздачи ниже минимума сидов и экранки.
Приоритет: больше сидов, затем меньший размер. Ограничения применяются к размеру
всей раздачи, а не одной серии или одному файлу. Если совпадений нет, доступны
изменение условий и ручной выбор. Ручной выбор не применяет эти ограничения.

Для заявок из нового экрана qBittorrent получает stopCondition=MetadataReceived.
После получения metadata бот проверяет фактический размер; при выходе за диапазон
оставляет torrent на паузе и требует отдельного подтверждения. Сборники с несколькими
видеофайлами дополнительно проходят существующий ручной выбор файлов.
Требуется qBittorrent с поддержкой stopCondition=MetadataReceived.

При выборе inline-подсказки в личном чате обработчик читает callback карточки из
входящего сообщения. Также поддержан chosen_inline_result (если inline feedback
включён у бота); дубли этих событий объединяются. Для inline-карточек в других
чатах остаётся кнопка «Подобрать раздачу», открывающая личное подтверждение.