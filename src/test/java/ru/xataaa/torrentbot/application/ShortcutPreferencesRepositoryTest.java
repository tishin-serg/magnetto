package ru.xataaa.torrentbot.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ShortcutPreferencesRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ShortcutPreferencesRepository repository = new ShortcutPreferencesRepository(jdbc);

    @Test
    void reusesMigratedUserWhenTelegramChatAlreadyExists() {
        UUID configuredUserId = UUID.randomUUID();
        UUID migratedUserId = UUID.randomUUID();
        long telegramChatId = 42L;
        when(jdbc.query(eq("select id from app_user where telegram_chat_id=?"), any(RowMapper.class), eq(telegramChatId)))
                .thenReturn(List.of(migratedUserId));

        UUID resolvedUserId = repository.ensureUser(configuredUserId, telegramChatId);

        assertEquals(migratedUserId, resolvedUserId);
        verify(jdbc, never()).update(any(String.class), any(), any());
    }

    @Test
    void createsConfiguredUserWhenTelegramChatIsNew() {
        UUID configuredUserId = UUID.randomUUID();
        long telegramChatId = 42L;
        when(jdbc.query(eq("select id from app_user where telegram_chat_id=?"), any(RowMapper.class), eq(telegramChatId)))
                .thenReturn(List.of(), List.of(configuredUserId));

        UUID resolvedUserId = repository.ensureUser(configuredUserId, telegramChatId);

        assertEquals(configuredUserId, resolvedUserId);
        verify(jdbc).update("insert into app_user(id,telegram_chat_id) values(?,?) on conflict do nothing", configuredUserId, telegramChatId);
    }
}
