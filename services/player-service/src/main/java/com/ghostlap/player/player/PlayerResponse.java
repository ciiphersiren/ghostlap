package com.ghostlap.player.player;

import java.time.Instant;

public record PlayerResponse(long playerId, String username, Instant createdAt) {
}
