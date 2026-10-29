package com.ghostlap.net;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * A logged-in player. Kept in memory only (never written to disk), so closing the game logs out.
 *
 * @param token the JWT from player-service, sent as "Authorization: Bearer token"
 */
public record Session(long playerId, String username, String token, Instant expiresAt) {

    /**
     * A JWT's middle part (the payload) is plain base64url JSON, readable by anyone. Only the
     * signature is secret. We read the username and expiry from it, so the game knows when to ask
     * the player to log in again. (The client cannot check the signature; the servers do that.)
     */
    public static Session fromToken(long playerId, String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Not a JWT");
        }
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        JsonValue payload = new JsonReader().parse(payloadJson);
        return new Session(playerId, payload.getString("username"), token,
                Instant.ofEpochSecond(payload.getLong("exp")));
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
