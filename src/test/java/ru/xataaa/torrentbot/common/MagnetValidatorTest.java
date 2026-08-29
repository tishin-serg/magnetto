package ru.xataaa.torrentbot.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MagnetValidatorTest {

    private final MagnetValidator magnetValidator = new MagnetValidator();

    @Test
    void shouldAcceptValidMagnet() {
        assertThat(magnetValidator.isValid("magnet:?xt=urn:btih:abcdef123456")).isTrue();
    }

    @Test
    void shouldRejectInvalidValues() {
        assertThat(magnetValidator.isValid(null)).isFalse();
        assertThat(magnetValidator.isValid("")).isFalse();
        assertThat(magnetValidator.isValid("https://example.com/file.torrent")).isFalse();
        assertThat(magnetValidator.isValid("magnet:?xt=urn:sha1:abcdef")).isFalse();
    }
}
