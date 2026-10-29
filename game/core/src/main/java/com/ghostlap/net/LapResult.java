package com.ghostlap.net;

/**
 * race-service's verdict on an uploaded lap.
 *
 * @param reason rejection reason code (e.g. TOO_FAST), null if accepted
 * @param rank   leaderboard position after this lap, null if rejected
 */
public record LapResult(long lapId, boolean accepted, String reason, Integer rank) {
}
