package ru.xataaa.torrentbot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.config.AppProperties;
import ru.xataaa.torrentbot.job.DownloadJobService;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;
import ru.xataaa.torrentbot.movie.MovieMetadata;
import ru.xataaa.torrentbot.movie.MovieSearchSessionService;
import ru.xataaa.torrentbot.preferences.*;
import ru.xataaa.torrentbot.torrentsearch.*;

@Service
@RequiredArgsConstructor
public class MovieDownloadConfirmationService implements TelegramCallbackHandler {
    private final AppProperties appProperties;
    private final DownloadPreferencesRepository preferencesRepository;
    private final TorrentAvailabilityService availabilityService;
    private final TorrentSearchService searchService;

    private final TelegramMessageService messages;
    private final DownloadJobService jobs;
    private final S3MediaLibraryService s3;
    private final ObjectMapper mapper;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Input> inputs = new ConcurrentHashMap<>();

    private static class Session {
        final String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        final long owner;
        final MovieMetadata movie;
        final Instant expires = Instant.now().plusSeconds(1800);
        DownloadPreferences preferences;
        List<TorrentAvailabilityItem> candidates = List.of();
        int index;
        int revision;
        boolean closed;
        boolean editing;
        Session(long owner, MovieMetadata movie, DownloadPreferences preferences) {
            this.owner = owner; this.movie = movie; this.preferences = preferences;
        }
    }
    private record Input(String sessionId, String field, int revision, Instant expires, Long messageId) {}

    public void leaveInput(Long chatId) {
        Input input = inputs.remove(chatId);
        if (input == null) return;
        Session session = sessions.get(input.sessionId);
        if (session != null) synchronized (session) { session.revision++; }
    }

    private boolean allowed(Long chatId) {
        return chatId != null && chatId > 0 && appProperties.isChatAllowed(chatId);
    }

    public void settings(Long chatId) {
        settings(chatId, null);
    }

    public void settings(Long chatId, Long messageId) {
        if (!allowed(chatId)) {
            messages.sendText(chatId, "Открой настройки в личном чате с ботом.");
            return;
        }
        Session session = create(chatId, null);
        session.editing = true;
        editor(session, messageId);
    }

    public void open(Long chatId, Long messageId, MovieMetadata movie) {
        if (!allowed(chatId)) return;
        Session session = create(chatId, movie);
        messages.sendTyping(chatId);
        try {
            refresh(session);
            show(session, messageId);
        } catch (RuntimeException exception) {
            sessions.remove(session.id);
            messages.sendTextWithInlineKeyboard(chatId, "Не удалось подобрать раздачу. Источник временно недоступен. Попробуй поиск позже.", new TelegramKeyboardFactory().searchLauncherKeyboard());
        }
    }

    private Session create(long chatId, MovieMetadata movie) {
        sessions.entrySet().removeIf(e -> e.getValue().expires.isBefore(Instant.now()));
        inputs.remove(chatId);
        if (movie == null) sessions.values().stream().filter(s -> s.owner == chatId && s.movie == null)
                .forEach(s -> { synchronized (s) { s.closed = true; } });
        Session session = new Session(chatId, movie, preferencesRepository.find(chatId));
        sessions.put(session.id, session);
        return session;
    }

    private void refresh(Session session) {
        session.candidates = availabilityService.catalog(session.movie).items().stream()
                .filter(item -> session.preferences.accepts(item.result()))
                .filter(item -> !isScreenCopy(item.result().title()) && !isCompilation(item.result().title()))
                .sorted(Comparator.comparingInt(TorrentAvailabilityItem::seeders).reversed()
                        .thenComparingLong(item -> item.result().sizeBytes()))
                .toList();
        session.index = 0;
    }

    static boolean isScreenCopy(String title) {
        return title != null && java.util.regex.Pattern.compile(
                "(?iu)(?<![\\p{L}\\d])(cam(?:rip)?|hdcam|ts|telesync|telecine|tc|экранка)(?![\\p{L}\\d])")
                .matcher(title).find();
    }

    static boolean isCompilation(String title) {
        return title != null && java.util.regex.Pattern.compile(
                "(?iu)(трилоги|дилоги|тетралоги|пенталоги|коллекци|антологи|сборник|trilogy|duology|collection|anthology|complete[ -]pack|[12]\\d{3}\\s*[-–/]\\s*[12]\\d{3})")
                .matcher(title).find();
    }

    private static String displaySize(long bytes) {
        return java.math.BigDecimal.valueOf(bytes, 9).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    @Override public boolean supports(String data) { return data != null && data.startsWith("pref:"); }

    @Override public void handle(String queryId, Long chatId, Long messageId, String data) {
        if (!allowed(chatId)) { messages.answerCallbackQuery(queryId, "Открой личный чат с ботом"); return; }
        String[] parts = data.split(":");
        if (parts.length < 4) { messages.answerCallbackQuery(queryId, "Кнопка устарела"); return; }
        Session session = sessions.get(parts[2]);
        if (session == null || session.owner != chatId || session.expires.isBefore(Instant.now())) {
            messages.answerCallbackQuery(queryId, "Экран устарел. Открой поиск или настройки заново.");
            if (session == null || session.owner == chatId)
                messages.sendTextWithInlineKeyboard(chatId, "Этот экран устарел. Сохранённые настройки не потеряны.", new TelegramKeyboardFactory().mainMenuKeyboard());
            return;
        }
        synchronized (session) {
            if (session.closed || !Integer.toString(session.revision).equals(parts[3])) {
                messages.answerCallbackQuery(queryId, "Этот экран уже изменился"); return;
            }
            messages.answerCallbackQuery(queryId, "Принято");
            String action = parts[1];
            if (action.equals("confirm")) {
                if (session.editing || session.movie == null || session.candidates.isEmpty()) return;
                if (session.preferences.target().isS3() && !(s3.isEnabled() && s3.isConfigured())) {
                    messages.sendText(chatId, "S3 сейчас недоступен. Выбери другое место в условиях заявки."); return;
                }
                TorrentSearchResult result = session.candidates.get(session.index).result();
                List<TorrentSearchResult> alternatives = session.candidates.stream().map(TorrentAvailabilityItem::result).toList();
                session.closed = true;
                inputs.remove(chatId);
                // Consume confirmation before invoking the job service: repeated clicks cannot create a second job.
                render(session, messageId, "Подтверждено. Создаю задачу…", List.of());
                jobs.startDownload(chatId, result.magnetUri(), result.sizeBytes(),
                        session.preferences.target(), result.title(), session.preferences, alternatives);
                return;
            }
            session.revision++;
            inputs.remove(chatId);
            if (action.equals("cancel")) {
                session.closed = true; render(session, messageId, session.movie == null
                        ? "Настройки сохранены.\n\n" + session.preferences.summary()
                        : "Заявка отменена. Загрузка не запускалась.", homeRows()); return;
            }
            if (action.equals("edit")) {
                session.editing = true; editor(session, messageId); return;
            }
            if (action.equals("field") && parts.length == 5) {
                if (!session.editing || !Set.of("min", "max", "seeds", "speed").contains(parts[4])) return;
                inputs.put(chatId, new Input(session.id, parts[4], session.revision, Instant.now().plusSeconds(600), messageId));
                String prompt = parts[4].equals("seeds") ? "Введи минимальное число сидов (целое, от 1)."
                        : parts[4].equals("speed") ? "Введи минимальную скорость в МБ/с. Значение 0 отключает мониторинг."
                        : "Введи размер «" + (parts[4].equals("min") ? "от" : "до") + "» в ГБ, например 4,5.";
                render(session, messageId, prompt + "\n\nСейчас: " + session.preferences.summary(), List.of(List.of(button(session, "← Назад", "edit")))); return;
            }
            if (action.equals("target") && parts.length == 5 && session.editing) {
                if (parts[4].equals("S3") && !(s3.isEnabled() && s3.isConfigured())) {
                    render(session, messageId, "Облако S3 недоступно. Выбери домашний ПК или сервер.",
                            List.of(List.of(button(session, "← Настройки", "edit")))); return;
                }
                session.preferences = session.preferences.with("target", parts[4]);
                saveDefaults(session); editor(session, messageId); return;
            }
            if (action.equals("auto") && session.editing) {
                session.preferences = session.preferences.with("auto", Boolean.toString(!session.preferences.autoReplaceSlowDownload()));
                saveDefaults(session); editor(session, messageId); return;
            }
            if (action.equals("back")) {
                if (session.movie == null) {
                    session.closed = true; render(session, messageId, "✅ Настройки сохранены.\n\n" + session.preferences.summary(), homeRows());
                } else {
                    refresh(session); session.editing = false; show(session, messageId);
                }
                return;
            }
            if (action.equals("manual") && session.movie != null) {
                session.closed = true;
                var results = availabilityService.catalog(session.movie).items().stream().map(TorrentAvailabilityItem::result).toList();
                String searchId = searchService.storeSearchPage(session.movie.title(), results);
                var page = searchService.findPage(searchId, 0);
                messages.editText(chatId, messageId, searchService.formatPageMessage(page), searchService.resultsKeyboard(page));
                return;
            }
            if (action.equals("next") && !session.editing && !session.candidates.isEmpty())
                session.index = (session.index + 1) % session.candidates.size();
            show(session, messageId);
        }
    }

    public boolean consumeInput(Long chatId, String text) {
        Input input = inputs.get(chatId);
        if (input == null) return false;
        if (text == null || text.trim().startsWith("/") || text.trim().startsWith("magnet:")) {
            leaveInput(chatId); return false;
        }
        if (input.expires.isBefore(Instant.now())) {
            inputs.remove(chatId, input);
            messages.sendTextWithInlineKeyboard(chatId, "Время ввода истекло. Открой настройки и выбери параметр заново.", new TelegramKeyboardFactory().mainMenuKeyboard());
            return true;
        }
        Session session = sessions.get(input.sessionId);
        if (!allowed(chatId) || session == null || session.expires.isBefore(Instant.now())) {
            inputs.remove(chatId, input); return false;
        }
        synchronized (session) {
            if (session.closed || session.revision != input.revision || !session.editing) {
                inputs.remove(chatId, input); return false;
            }
            try {
                session.preferences = session.preferences.with(input.field, text);
            } catch (IllegalArgumentException exception) {
                render(session, input.messageId, exception.getMessage() + "\n\nПопробуй другое число или вернись к настройкам.",
                        List.of(List.of(button(session, "← Назад", "edit")))); return true;
            }
            saveDefaults(session);
            inputs.remove(chatId, input);
            session.revision++;
            editor(session, input.messageId);
            return true;
        }
    }

    private void saveDefaults(Session session) {
        if (session.movie == null) preferencesRepository.save(session.owner, session.preferences);
    }

    private void editor(Session session, Long messageId) {
        String text = (session.movie == null ? "⚙️ Настройки следующих загрузок" : "⚙️ Условия только этой заявки")
                + "\n\n" + session.preferences.summary() + (session.movie == null ? "\n\nИзменения сохраняются сразу." : "\n\nНастройки следующих загрузок не изменятся.");
        render(session, messageId, text, List.of(
                List.of(button(session, "Размер от", "field:min"), button(session, "Размер до", "field:max")),
                List.of(button(session, "Минимум сидов", "field:seeds")),
                List.of(button(session, "Мин. скорость", "field:speed")),
                List.of(button(session, session.preferences.autoReplaceSlowDownload() ? "🤖 Автозамена: вкл" : "🤖 Автозамена: выкл", "auto")),
                List.of(button(session, "Домашний ПК", "target:HOME_PC"), button(session, "VPS", "target:VPS")),
                List.of(button(session, "Облако · S3", "target:S3")),
                List.of(button(session, session.movie == null ? "Готово" : "Подобрать раздачу", "back")),
                List.of(session.movie == null ? Map.of("text", "🏠 Главное меню", "callback_data", "menu:home")
                        : button(session, "❌ Отменить заявку", "cancel"))));
    }

    private void show(Session session, Long messageId) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        String text = "🎬 " + session.movie.title() + (session.movie.year() == null ? "" : " (" + session.movie.year() + ")")
                + "\n\n" + session.preferences.summary();
        if (session.candidates.isEmpty()) {
            text += "\n\nПодходящих раздач нет. Можно изменить условия или выбрать вручную.";
        } else {
            var item = session.candidates.get(session.index);
            text += "\n\nРаздача: " + item.result().title()
                    + "\n📦 Размер: " + displaySize(item.result().sizeBytes()) + " ГБ"
                    + "\n🎞 Качество: " + item.quality() + "\n🔊 Озвучка: " + item.voice()
                    + "\n🌱 Сиды: " + item.seeders()
                    + "\nВариант " + (session.index + 1) + " из " + session.candidates.size()
                    + "\n\nНачну скачивание только после подтверждения.";
            rows.add(List.of(button(session, "✅ Скачать", "confirm")));
            if (session.candidates.size() > 1) rows.add(List.of(button(session, "Другой вариант", "next")));
        }
        rows.add(List.of(button(session, "Изменить условия", "edit")));
        rows.add(List.of(button(session, "Выбрать вручную", "manual"), button(session, "Отмена", "cancel")));
        render(session, messageId, text, rows);
    }

    private Map<String, String> button(Session session, String label, String action) {
        String[] parts = action.split(":", 2);
        return Map.of("text", label, "callback_data", "pref:" + parts[0] + ":" + session.id + ":" + session.revision
                + (parts.length == 2 ? ":" + parts[1] : ""));
    }

    private List<List<Map<String, String>>> homeRows() {
        return List.of(List.of(Map.of("text", "🔎 Поиск", "callback_data", "menu:search")),
                List.of(Map.of("text", "🏠 Главное меню", "callback_data", "menu:home")));
    }

    private void render(Session session, Long messageId, String text, List<List<Map<String, String>>> rows) {
        try {
            String keyboard = mapper.writeValueAsString(Map.of("inline_keyboard", rows));
            if (messageId == null) messages.sendTextWithInlineKeyboard(session.owner, text, keyboard);
            else messages.editText(session.owner, messageId, text, keyboard);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
