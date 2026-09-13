package ru.xataaa.torrentbot.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ShortcutPreferencesTest {
    @Test
    void defaultsArePermissiveAndStable() {
        ShortcutPreferences preferences = ShortcutPreferences.defaults();
        assertEquals(0, preferences.minBytes());
        assertEquals(Long.MAX_VALUE, preferences.maxBytes());
        assertEquals("any", preferences.quality());
        assertEquals("any", preferences.voice());
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ShortcutPreferences(10, 9, 1, "any", "any", 0, false));
        assertThrows(IllegalArgumentException.class, () -> new ShortcutPreferences(0, 10, 0, "any", "any", 0, false));
    }

    @Test
    void deliveryTargetsHaveExplicitExecutionSemantics() {
        assertEquals(5, DeliveryTarget.values().length);
        assertTrue(java.util.Set.of(DeliveryTarget.PHONE_VPS_TEMP, DeliveryTarget.PHONE_S3_TEMP)
                .contains(DeliveryTarget.PHONE_VPS_TEMP));
        assertEquals(2, ExecutionTarget.values().length);
    }
}
