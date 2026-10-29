package com.ghostlap.net;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** Builds JWT-shaped strings for tests. The signature is fake; the client never checks it anyway. */
final class TestJwt {

    private TestJwt() {
    }

    static String token(String username, Instant expiresAt) {
        Base64.Encoder base64url = Base64.getUrlEncoder().withoutPadding();
        String header = base64url.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64url.encodeToString(("{\"iss\":\"ghostlap\",\"sub\":\"7\",\"username\":\"" + username
                + "\",\"exp\":" + expiresAt.getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".fake-signature";
    }
}
