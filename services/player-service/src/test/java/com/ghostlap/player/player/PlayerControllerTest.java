package com.ghostlap.player.player;

import com.ghostlap.player.IntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayerControllerTest extends IntegrationTest {

    @Test
    void meReturnsTheTokenOwnersProfile() throws Exception {
        String token = registerAndGetToken("reet", "password123");

        mvc.perform(get("/api/v1/players/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("reet"))
                .andExpect(jsonPath("$.playerId").value(notNullValue()))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()));
    }

    @Test
    void meWithoutTokenIsUnauthorizedProblem() throws Exception {
        mvc.perform(get("/api/v1/players/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        String forged = token("a-completely-different-secret-of-32-bytes!", Instant.now().plusSeconds(3600));

        mvc.perform(get("/api/v1/players/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        // Spring allows 60 s of clock skew, so expire it well before that.
        String expired = token(TEST_JWT_SECRET, Instant.now().minusSeconds(3600));

        mvc.perform(get("/api/v1/players/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenForDeletedPlayerIsNotFound() throws Exception {
        String token = registerAndGetToken("reet", "password123");
        players.deleteAll();

        mvc.perform(get("/api/v1/players/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /** Builds a token by hand, with any secret and expiry. */
    private static String token(String secret, Instant expiresAt) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ghostlap")
                .subject("1")
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
