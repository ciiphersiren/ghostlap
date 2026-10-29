package com.ghostlap.player.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "ghostlap", Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void missingSecretIsRefused() {
        assertThatThrownBy(() -> new JwtProperties(null, "ghostlap", Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secretOf32BytesIsAccepted() {
        assertThatCode(() -> new JwtProperties("x".repeat(32), "ghostlap", Duration.ofHours(1)))
                .doesNotThrowAnyException();
    }
}
