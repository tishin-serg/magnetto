package ru.xataaa.torrentbot.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.job.DownloadTarget;

class DownloadPreferencesSpeedTest {
    @Test void zeroDisablesMonitoringAndDecimalMegabytesAreAccepted() {
        DownloadPreferences defaults = DownloadPreferences.defaults();
        assertThat(defaults.with("speed", "0").minDownloadSpeedBytesPerSecond()).isZero();
        assertThat(defaults.with("speed", "2,5").minDownloadSpeedBytesPerSecond()).isEqualTo(2_500_000);
    }

    @Test void automaticDecisionCanBeEnabledWithoutChangingOtherFields() {
        DownloadPreferences preferences = new DownloadPreferences(1, 10, 2, DownloadTarget.VPS, 2_000_000, false)
                .with("auto", "true");
        assertThat(preferences.autoReplaceSlowDownload()).isTrue();
        assertThat(preferences.target()).isEqualTo(DownloadTarget.VPS);
        assertThat(preferences.summary()).contains("2 МБ/с", "Автозамена медленной раздачи: включена");
    }
}
