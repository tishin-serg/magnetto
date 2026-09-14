package ru.xataaa.torrentbot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.scheduler.Schedulers;
import ru.xataaa.torrentbot.application.ApiIdempotencyRepository;
import ru.xataaa.torrentbot.application.DeliveryTarget;
import ru.xataaa.torrentbot.application.DownloadApplicationService;
import ru.xataaa.torrentbot.application.DownloadControlService;
import ru.xataaa.torrentbot.application.DownloadQueryService;
import ru.xataaa.torrentbot.application.ShortcutPreferences;
import ru.xataaa.torrentbot.application.ShortcutPreferencesRepository;
import ru.xataaa.torrentbot.config.ShortcutApiProperties;

class ShortcutApiControllerUserResolutionTest {

    @Test
    void createsDownloadForUserResolvedFromTelegramMigration() throws Exception {
        UUID configuredUserId = UUID.randomUUID();
        UUID migratedUserId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        long telegramChatId = 42L;
        DownloadApplicationService application = mock(DownloadApplicationService.class);
        ShortcutPreferencesRepository preferences = mock(ShortcutPreferencesRepository.class);
        ApiIdempotencyRepository idempotency = mock(ApiIdempotencyRepository.class);
        ShortcutApiProperties properties = new ShortcutApiProperties(true, configuredUserId, telegramChatId,
                sha256("test-token"), 30, 5, "https://example.test", 24, "temporary/");
        ShortcutApiController controller = new ShortcutApiController(properties, application,
                mock(DownloadQueryService.class), mock(DownloadControlService.class), preferences, idempotency);
        ShortcutPreferences profile = ShortcutPreferences.defaults();
        when(preferences.ensureUser(configuredUserId, telegramChatId)).thenReturn(migratedUserId);
        when(idempotency.find(migratedUserId, "request-1")).thenReturn(Optional.empty());
        when(preferences.find(migratedUserId)).thenReturn(profile);
        when(application.create(eq(migratedUserId), eq(telegramChatId), eq("movie-selection"), eq(null),
                eq(null), eq(Set.of()), eq(null), eq(null), eq(DeliveryTarget.HOME_LIBRARY), eq(true), eq(profile)))
                .thenAnswer(invocation -> {
                    assertFalse(Schedulers.isInNonBlockingThread());
                    return jobId;
                });
        when(idempotency.saveIfAbsent(migratedUserId, "request-1", jobId)).thenReturn(true);

        ResponseEntity<?> response = controller.create("Bearer test-token", "request-1",
                new ShortcutApiController.CreateRequest("movie-selection", null, null, Set.of(), null, null,
                        DeliveryTarget.HOME_LIBRARY, true)).block();

        assertEquals(202, response.getStatusCode().value());
        verify(application).create(eq(migratedUserId), eq(telegramChatId), eq("movie-selection"), eq(null),
                eq(null), eq(Set.of()), eq(null), eq(null), eq(DeliveryTarget.HOME_LIBRARY), eq(true), eq(profile));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
