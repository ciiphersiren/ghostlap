package com.ghostlap.race.ghost;

import java.time.Instant;

/** @param ghost the GhostCodec bytes, base64-encoded (decode with GhostCodec.decode) */
public record GhostResponse(long lapId, String username, int trackId, int lapTimeMs, Instant submittedAt, String ghost) {
}
