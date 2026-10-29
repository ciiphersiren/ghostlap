package com.ghostlap.race;

import com.ghostlap.shared.GameConstants;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostCodec;
import com.ghostlap.shared.ghost.GhostSample;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check without a window: the Autopilot drives the real car physics around the real
 * track and must complete valid laps. Each lap must also be one race-service will accept.
 */
class LapSimulationTest {

    @Test
    void autopilotCompletesValidLaps() throws IOException {
        Autopilot.Run run = Autopilot.drive(2);

        assertThat(run.laps()).hasSize(2);
        assertThat(run.offTrackTicks()).isZero();
        Autopilot.DrivenLap last = run.laps().get(1);
        assertThat(last.lapTimeMs()).isEqualTo(run.timer().getLastLapMs());
        assertLapIsValid(last);
        // A lap can't be faster than driving the whole centerline at top speed.
        float fastestPossibleMs = run.track().getLength() / GameConstants.MAX_SPEED * 1000f;
        assertThat(run.timer().getBestLapMs()).isGreaterThan((int) fastestPossibleMs);
        System.out.printf("Autopilot best lap: %s (track length %.0f units)%n",
                LapTimer.formatTime(run.timer().getBestLapMs()), run.track().getLength());
    }

    /** The checks race-service runs, applied to a lap from real driving. */
    private static void assertLapIsValid(Autopilot.DrivenLap lap) throws IOException {
        Ghost ghost = lap.ghost();
        assertThat(ghost.lapTimeMs()).isEqualTo(lap.lapTimeMs());

        // CHECKPOINT_ORDER: one time per checkpoint, strictly increasing, the last one is the lap time
        assertThat(lap.checkpointTimesMs()).hasSize(GameConstants.CHECKPOINT_COUNT).isSorted().doesNotHaveDuplicates();
        assertThat(lap.checkpointTimesMs()[0]).isPositive();
        assertThat(lap.checkpointTimesMs()[GameConstants.CHECKPOINT_COUNT - 1]).isEqualTo(lap.lapTimeMs());

        // SAMPLE_COUNT_MISMATCH: count must be lapTimeMs x 30 Hz, +-2
        float expectedSamples = lap.lapTimeMs() * GameConstants.GHOST_SAMPLE_RATE_HZ / 1000f;
        assertThat((float) ghost.sampleCount()).isBetween(expectedSamples - 2, expectedSamples + 2);

        // SPEED_EXCEEDED: no jump between samples faster than MAX_SPEED x 1.1
        List<GhostSample> s = ghost.samples();
        float secondsPerSample = 1f / ghost.sampleRateHz();
        for (int i = 1; i < s.size(); i++) {
            float distance = (float) Math.hypot(s.get(i).x() - s.get(i - 1).x(), s.get(i).y() - s.get(i - 1).y());
            assertThat(distance / secondsPerSample).isLessThanOrEqualTo(GameConstants.MAX_SPEED * 1.1f);
        }

        byte[] encoded = GhostCodec.encode(ghost);
        assertThat(GhostCodec.decode(encoded)).isEqualTo(ghost);
        // Keep a real ghost file around (build/tmp) for inspecting sizes or loading in the game.
        Files.createDirectories(Path.of("build", "tmp"));
        Files.write(Path.of("build", "tmp", "autopilot-lap.ghost"), encoded);
        int rawBytes = GhostCodec.HEADER_BYTES + ghost.sampleCount() * GhostCodec.BYTES_PER_SAMPLE;
        System.out.printf("Autopilot ghost: %d samples, %d bytes raw, %d bytes gzipped%n",
                ghost.sampleCount(), rawBytes, encoded.length);
    }
}
