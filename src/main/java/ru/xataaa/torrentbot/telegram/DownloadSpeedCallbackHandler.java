package ru.xataaa.torrentbot.telegram;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.xataaa.torrentbot.job.DownloadJob;
import ru.xataaa.torrentbot.job.DownloadJobRepository;
import ru.xataaa.torrentbot.speed.DownloadAlternativeRepository;
import ru.xataaa.torrentbot.speed.DownloadSpeedDecisionService;
import ru.xataaa.torrentbot.speed.DownloadSpeedMonitorLifecycle;

@Component
@RequiredArgsConstructor
public class DownloadSpeedCallbackHandler implements TelegramCallbackHandler {
    private final DownloadJobRepository jobs;
    private final DownloadAlternativeRepository alternatives;
    private final DownloadSpeedDecisionService decisions;
    private final DownloadSpeedMonitorLifecycle lifecycle;
    private final TelegramMessageService messages;

    @Override public boolean supports(String data) { return data != null && data.startsWith("speed:"); }

    @Override
    public synchronized void handle(String queryId, Long chatId, Long messageId, String data) {
        String[] parts = data.split(":");
        if (parts.length < 3) { messages.answerCallbackQuery(queryId, "Некорректная кнопка"); return; }
        DownloadJob job = find(parts[2]);
        if (job == null || !job.getChatId().equals(chatId)) {
            messages.answerCallbackQuery(queryId, "Задача не найдена"); return;
        }
        if ("keep".equals(parts[1])) {
            lifecycle.stop(job.getId());
            messages.answerCallbackQuery(queryId, "Оставляю текущую раздачу");
            messages.editText(chatId, messageId, "Текущая загрузка продолжится без автоматической замены.", "{\"inline_keyboard\":[]}");
            return;
        }
        if ("list".equals(parts[1])) {
            var items = alternatives.findByJobId(job.getId());
            if (items.isEmpty()) {
                messages.answerCallbackQuery(queryId, "Альтернатив нет");
                messages.editText(chatId, messageId, "Сохранённых альтернатив нет. Запусти новый поиск.",
                        "{\"inline_keyboard\":[[{\"text\":\"🔎 Новый поиск\",\"callback_data\":\"menu:search\"}]]}");
                return;
            }
            StringBuilder keyboard = new StringBuilder("{\"inline_keyboard\":[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) keyboard.append(',');
                var item = items.get(i);
                keyboard.append("[{\"text\":\"").append(escape(shortTitle(item.title())))
                        .append("\",\"callback_data\":\"speed:choose:").append(job.getId()).append(':').append(item.position()).append("\"}]");
            }
            keyboard.append("]}");
            messages.answerCallbackQuery(queryId, "Выбери замену");
            messages.editText(chatId, messageId, "Текущая загрузка продолжается. Выбери замену для окончательного подтверждения:", keyboard.toString());
            return;
        }
        if ("choose".equals(parts[1]) && parts.length == 4) {
            int position;
            try { position = Integer.parseInt(parts[3]); }
            catch (NumberFormatException exception) { messages.answerCallbackQuery(queryId, "Некорректный вариант"); return; }
            var selected = alternatives.findByJobId(job.getId()).stream().filter(item -> item.position() == position).findFirst().orElse(null);
            if (selected == null || !decisions.replace(job, selected)) {
                messages.answerCallbackQuery(queryId, "Выбор уже обработан или устарел"); return;
            }
            messages.answerCallbackQuery(queryId, "Запускаю другую раздачу");
            messages.editText(chatId, messageId, "Текущая раздача поставлена на паузу, данные сохранены. Новая заявка создана.", "{\"inline_keyboard\":[]}");
        }
    }

    private DownloadJob find(String id) {
        try { return jobs.findById(UUID.fromString(id)).orElse(null); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private String shortTitle(String title) {
        if (title == null || title.isBlank()) return "Другая раздача";
        return title.length() <= 48 ? title : title.substring(0, 45) + "...";
    }

    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
