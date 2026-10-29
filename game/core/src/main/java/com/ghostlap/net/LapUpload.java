package com.ghostlap.net;

import com.ghostlap.shared.ghost.Ghost;

/** A finished lap to send to race-service. */
public record LapUpload(int trackId, int lapTimeMs, int[] checkpointTimesMs, Ghost ghost) {
}
