package com.ghostlap.race;

import com.ghostlap.race.lap.LapSubmissionRequest;
import com.ghostlap.shared.GameConstants;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostCodec;
import com.ghostlap.shared.ghost.GhostSample;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Builds believable lap submissions for tests: a car driving a circle at constant speed. */
public final class TestLaps {

    public static final float SPEED = 500f; // below MAX_SPEED (600)

    private TestLaps() {
    }

    /** A lap that passes every validation rule. */
    public static LapSubmissionRequest validLap(int lapTimeMs) {
        return new LapSubmissionRequest(GameConstants.TRACK_ID, lapTimeMs, checkpointTimes(lapTimeMs),
                base64(circleGhost(lapTimeMs, expectedSamples(lapTimeMs))));
    }

    /** The same lap with a different ghost. */
    public static LapSubmissionRequest withGhost(LapSubmissionRequest lap, Ghost ghost) {
        return new LapSubmissionRequest(lap.trackId(), lap.lapTimeMs(), lap.checkpointTimesMs(), base64(ghost));
    }

    /** What the game records: one sample every 2 ticks, from tick 0, so lapTicks/2 + 1 samples. */
    public static int expectedSamples(int lapTimeMs) {
        return lapTimeMs * GameConstants.GHOST_SAMPLE_RATE_HZ / 1000 + 1;
    }

    /** One time per checkpoint, evenly spaced, the last one at the finish line. */
    public static List<Integer> checkpointTimes(int lapTimeMs) {
        List<Integer> times = new ArrayList<>();
        for (int i = 1; i <= GameConstants.CHECKPOINT_COUNT; i++) {
            times.add(lapTimeMs * i / GameConstants.CHECKPOINT_COUNT);
        }
        return times;
    }

    /** One lap around a circle sized so that the car drives it at SPEED. */
    public static Ghost circleGhost(int lapTimeMs, int sampleCount) {
        double lapSeconds = lapTimeMs / 1000.0;
        double radius = SPEED * lapSeconds / (2 * Math.PI);
        List<GhostSample> samples = new ArrayList<>();
        for (int i = 0; i < sampleCount; i++) {
            double t = (double) i / GameConstants.GHOST_SAMPLE_RATE_HZ;
            double angle = 2 * Math.PI * t / lapSeconds;
            samples.add(new GhostSample((float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle)),
                    (float) Math.atan2(Math.cos(angle), -Math.sin(angle)))); // heading = direction of travel
        }
        return new Ghost(GameConstants.TRACK_ID, GameConstants.GHOST_SAMPLE_RATE_HZ, lapTimeMs, samples);
    }

    public static String base64(Ghost ghost) {
        return Base64.getEncoder().encodeToString(GhostCodec.encode(ghost));
    }
}
