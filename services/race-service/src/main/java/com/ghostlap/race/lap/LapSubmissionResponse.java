package com.ghostlap.race.lap;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param reason only for REJECTED laps
 * @param rank   only for ACCEPTED laps: the player's position on the leaderboard (1 = first)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LapSubmissionResponse(long lapId, LapStatus status, RejectReason reason, Long rank) {

    public static LapSubmissionResponse accepted(long lapId, Long rank) {
        return new LapSubmissionResponse(lapId, LapStatus.ACCEPTED, null, rank);
    }

    public static LapSubmissionResponse rejected(long lapId, RejectReason reason) {
        return new LapSubmissionResponse(lapId, LapStatus.REJECTED, reason, null);
    }
}
