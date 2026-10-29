package com.ghostlap.race.track;

/**
 * What the server knows about a track. {@code minLapTimeMs} is the fastest lap we believe is
 * physically possible; anything quicker is rejected as TOO_FAST.
 */
public record Track(int id, String name, int minLapTimeMs) {
}
