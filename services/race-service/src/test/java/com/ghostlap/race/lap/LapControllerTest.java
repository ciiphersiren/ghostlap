package com.ghostlap.race.lap;

import com.ghostlap.race.IntegrationTest;
import com.ghostlap.race.TestLaps;
import com.ghostlap.race.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LapControllerTest extends IntegrationTest {

    private final String token = TestTokens.forPlayer(1, "reet");

    @Test
    void believableLapIsAcceptedRankedAndStored() throws Exception {
        submit(token, TestLaps.validLap(16_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.reason").doesNotExist());

        Lap lap = laps.findAll().getFirst();
        assertThat(lap.getStatus()).isEqualTo(LapStatus.ACCEPTED);
        assertThat(lap.getPlayerId()).isEqualTo(1);
        assertThat(lap.getUsername()).isEqualTo("reet");
        assertThat(lap.getCheckpointTimesMs()).hasSize(8).endsWith(16_000);
        assertThat(ghosts.findById(lap.getId())).isPresent();
    }

    @Test
    void rankIsThePlayersPositionOnTheBoard() throws Exception {
        submitAccepted(2, "fast", 14_000);

        submit(token, TestLaps.validLap(16_000))
                .andExpect(jsonPath("$.rank").value(2));
    }

    @Test
    void cheatingLapIsStoredAsRejectedWithReason() throws Exception {
        submit(token, TestLaps.validLap(9_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("TOO_FAST"))
                .andExpect(jsonPath("$.rank").doesNotExist());

        Lap lap = laps.findAll().getFirst();
        assertThat(lap.getStatus()).isEqualTo(LapStatus.REJECTED);
        assertThat(lap.getRejectReason()).isEqualTo(RejectReason.TOO_FAST);
        assertThat(lap.getRejectDetail()).contains("below the track minimum");
        assertThat(ghosts.findById(lap.getId())).isEmpty();          // no ghost kept
        assertThat(redis.hasKey("leaderboard:track:1")).isFalse();   // never reaches the leaderboard
    }

    @Test
    void corruptGhostIsRejectedAsBadGhost() throws Exception {
        LapSubmissionRequest lap = TestLaps.validLap(16_000);
        LapSubmissionRequest corrupt = new LapSubmissionRequest(lap.trackId(), lap.lapTimeMs(), lap.checkpointTimesMs(), "AAAA");

        submit(token, corrupt)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("BAD_GHOST"));
    }

    @Test
    void submittingWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/laps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(TestLaps.validLap(16_000))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(laps.count()).isZero();
    }

    @Test
    void tokenSignedWithAnotherSecretIsUnauthorized() throws Exception {
        submit(TestTokens.signedWithOtherSecret(1, "reet"), TestLaps.validLap(16_000))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutUsernameIsUnauthorized() throws Exception {
        submit(TestTokens.withoutUsername(1), TestLaps.validLap(16_000))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedRequestIsBadRequestWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/laps")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackId": 1, "lapTimeMs": -5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.lapTimeMs").exists())
                .andExpect(jsonPath("$.errors.checkpointTimesMs").exists())
                .andExpect(jsonPath("$.errors.ghost").exists());
    }

    @Test
    void unknownTrackIsNotFound() throws Exception {
        LapSubmissionRequest lap = TestLaps.validLap(16_000);
        LapSubmissionRequest otherTrack = new LapSubmissionRequest(99, lap.lapTimeMs(), lap.checkpointTimesMs(), lap.ghost());

        submit(token, otherTrack)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Track 99 not found"));
    }
}
