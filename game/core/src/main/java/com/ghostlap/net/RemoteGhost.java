package com.ghostlap.net;

import com.ghostlap.shared.ghost.Ghost;

/** A ghost downloaded from race-service, already decoded with the shared GhostCodec. */
public record RemoteGhost(long lapId, String username, Ghost ghost) {
}
