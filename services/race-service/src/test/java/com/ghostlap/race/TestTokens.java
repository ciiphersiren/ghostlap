package com.ghostlap.race;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Creates tokens the way player-service does, so race-service can be tested on its own. */
public final class TestTokens {

    private TestTokens() {
    }

    public static String forPlayer(long playerId, String username) {
        return sign(IntegrationTest.TEST_JWT_SECRET, JwtClaimsSet.builder()
                .issuer("ghostlap")
                .subject(String.valueOf(playerId))
                .claim("username", username)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());
    }

    public static String signedWithOtherSecret(long playerId, String username) {
        return sign("a-completely-different-secret-of-32-bytes!", JwtClaimsSet.builder()
                .issuer("ghostlap")
                .subject(String.valueOf(playerId))
                .claim("username", username)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());
    }

    public static String withoutUsername(long playerId) {
        return sign(IntegrationTest.TEST_JWT_SECRET, JwtClaimsSet.builder()
                .issuer("ghostlap")
                .subject(String.valueOf(playerId))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());
    }

    private static String sign(String secret, JwtClaimsSet claims) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
