package com.ghostlap.race.lap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of POST /api/v1/laps. These annotations only check the shape of the request (400 if wrong).
 * Whether the lap is believable is decided afterwards by LapValidator (ACCEPTED or REJECTED).
 *
 * @param checkpointTimesMs time since the lap started at each checkpoint, in order. The last one is
 *                          the finish line, so it equals lapTimeMs.
 * @param ghost             the GhostCodec bytes (gzipped GHST format), base64-encoded
 */
public record LapSubmissionRequest(
        @Positive int trackId,
        @Positive int lapTimeMs,
        @NotNull @Size(max = 100) List<@NotNull Integer> checkpointTimesMs,
        @NotBlank @Size(max = LapSubmissionRequest.MAX_GHOST_CHARS) String ghost) {

    /** About 1.4x the largest ghost GhostCodec accepts (10 minutes), after base64. */
    public static final int MAX_GHOST_CHARS = 400_000;
}
