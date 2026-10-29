package com.ghostlap.shared.ghost;

import java.util.List;

/**
 * A recorded lap: header fields plus samples taken {@code sampleRateHz} times per second.
 * Sample {@code i} is the car's state {@code i / sampleRateHz} seconds after crossing the start line.
 */
public record Ghost(int trackId, int sampleRateHz, int lapTimeMs, List<GhostSample> samples) {

    public Ghost {
        samples = List.copyOf(samples); // immutable, and rejects null samples
    }

    public int sampleCount() {
        return samples.size();
    }
}
