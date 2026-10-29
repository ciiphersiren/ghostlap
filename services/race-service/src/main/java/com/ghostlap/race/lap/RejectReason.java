package com.ghostlap.race.lap;

/** Why the anti-cheat validation rejected a lap. Returned to the client and stored with the lap. */
public enum RejectReason {
    /** The ghost's sample count doesn't match lapTimeMs x sample rate (+-2). */
    SAMPLE_COUNT_MISMATCH,
    /** The ghost moved faster than MAX_SPEED x 1.1 between two samples. */
    SPEED_EXCEEDED,
    /** Checkpoint times are not strictly increasing, the count is wrong, or the last isn't the lap time. */
    CHECKPOINT_ORDER,
    /** Faster than the track's minimum plausible lap time. */
    TOO_FAST,
    /** The ghost is not valid base64 / gzip / GHST data, or its header doesn't match the lap. */
    BAD_GHOST
}
