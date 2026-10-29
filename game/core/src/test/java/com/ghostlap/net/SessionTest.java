package com.ghostlap.net;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTest {

    @Test
    void readsUsernameAndExpiryFromTheTokenPayload() {
        Instant expiry = Instant.parse("2030-06-01T12:00:00Z");

        Session session = Session.fromToken(7, TestJwt.token("reet", expiry));

        assertThat(session.username()).isEqualTo("reet");
        assertThat(session.expiresAt()).isEqualTo(expiry);
        assertThat(session.isExpired()).isFalse();
    }

    @Test
    void knowsWhenTheTokenHasExpired() {
        Session session = Session.fromToken(7, TestJwt.token("reet", Instant.now().minusSeconds(1)));

        assertThat(session.isExpired()).isTrue();
    }

    @Test
    void refusesSomethingThatIsNotAJwt() {
        assertThatThrownBy(() -> Session.fromToken(7, "not-a-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
