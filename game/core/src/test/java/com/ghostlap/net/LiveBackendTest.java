package com.ghostlap.net;

import com.ghostlap.race.Autopilot;
import com.ghostlap.shared.GameConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Phase 4 scenario against the real backend (docker compose), with laps driven by the real game
 * physics: account A sets a lap, account B downloads A's ghost from the server.
 *
 * Skipped unless GHOSTLAP_LIVE_TEST=true, because it needs the services running:
 *   cd infra; docker compose up -d
 *   cd game; $env:GHOSTLAP_LIVE_TEST="true"; .\gradlew.bat test --tests "*LiveBackendTest"
 */
@EnabledIfEnvironmentVariable(named = "GHOSTLAP_LIVE_TEST", matches = "true")
class LiveBackendTest {

    private final ApiClient api = new ApiClient("http://localhost:8081", "http://localhost:8082", Runnable::run);
    private final String suffix = UUID.randomUUID().toString().substring(0, 6);

    @Test
    void playerBRacesPlayerAsGhostFromTheServer() throws Exception {
        Autopilot.DrivenLap lap = Autopilot.drive(2).laps().get(1);
        LapUpload upload = new LapUpload(GameConstants.TRACK_ID, lap.lapTimeMs(), lap.checkpointTimesMs(), lap.ghost());

        // Account A registers and uploads a lap driven by the real game physics.
        Session alice = Await.success(cb -> api.register("alice_" + suffix, "password123", cb));
        LapResult result = Await.success(cb -> api.submitLap(alice, upload, cb));
        assertThat(result.accepted()).as("rejected with %s", result.reason()).isTrue();
        assertThat(result.rank()).isNotNull();

        // Account B registers and downloads the top ghost: A's lap, byte for byte.
        Session bob = Await.success(cb -> api.register("bob_" + suffix, "password123", cb));
        RemoteGhost top = Await.success(cb -> api.fetchTopGhost(GameConstants.TRACK_ID, cb));
        assertThat(top.ghost()).isEqualTo(lap.ghost());
        System.out.printf("%s downloaded the top ghost: %s by %s (lap %d)%n",
                bob.username(), top.ghost().lapTimeMs(), top.username(), top.lapId());

        // A tampered lap (checkpoints out of order) is rejected with a reason.
        int[] shuffled = lap.checkpointTimesMs().clone();
        shuffled[2] = shuffled[1];
        LapResult cheat = Await.success(cb -> api.submitLap(bob,
                new LapUpload(GameConstants.TRACK_ID, lap.lapTimeMs(), shuffled, lap.ghost()), cb));
        assertThat(cheat.accepted()).isFalse();
        assertThat(cheat.reason()).isEqualTo("CHECKPOINT_ORDER");

        // The leaderboard shows A.
        List<LeaderboardRow> board = Await.success(cb -> api.fetchLeaderboard(GameConstants.TRACK_ID, 100, cb));
        assertThat(board).extracting(LeaderboardRow::username).contains(alice.username());

        // A forged token is refused.
        Session forged = new Session(1, "mallory", "aaa.bbb.ccc", Instant.now().plusSeconds(3600));
        ApiError error = Await.<LapResult>failure(cb -> api.submitLap(forged, upload, cb));
        assertThat(error.kind()).isEqualTo(ApiError.Kind.UNAUTHORIZED);
    }
}
