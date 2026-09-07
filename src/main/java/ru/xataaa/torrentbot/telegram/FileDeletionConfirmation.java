package ru.xataaa.torrentbot.telegram;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived, one-use confirmation bound to a chat, message and observed file version. */
final class FileDeletionConfirmation {
    private record Key(Long chat, Long message, String file) {}
    private record Pending(Object snapshot, Instant expires) {}
    private final Map<Key, Pending> pending = new ConcurrentHashMap<>();

    void ask(Long chat, Long message, String file, Object snapshot) {
        pending.entrySet().removeIf(e -> e.getKey().chat().equals(chat) || e.getValue().expires().isBefore(Instant.now()));
        pending.put(new Key(chat, message, file), new Pending(snapshot, Instant.now().plusSeconds(120)));
    }

    void cancel(Long chat, Long message, String file) {
        pending.remove(new Key(chat, message, file));
    }

    boolean consume(Long chat, Long message, String file, Object snapshot) {
        Pending value = pending.remove(new Key(chat, message, file));
        return value != null && value.expires().isAfter(Instant.now()) && Objects.equals(value.snapshot(), snapshot);
    }
}
