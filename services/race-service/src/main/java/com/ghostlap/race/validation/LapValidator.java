package com.ghostlap.race.validation;

import com.ghostlap.race.lap.LapSubmissionRequest;
import com.ghostlap.race.track.Track;
import com.ghostlap.shared.GameConstants;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostCodec;
import com.ghostlap.shared.ghost.GhostFormatException;
import com.ghostlap.shared.ghost.GhostSample;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

import static com.ghostlap.race.lap.RejectReason.BAD_GHOST;
import static com.ghostlap.race.lap.RejectReason.CHECKPOINT_ORDER;
import static com.ghostlap.race.lap.RejectReason.SAMPLE_COUNT_MISMATCH;
import static com.ghostlap.race.lap.RejectReason.SPEED_EXCEEDED;
import static com.ghostlap.race.lap.RejectReason.TOO_FAST;

/**
 * Server-side anti-cheat. The client could send any numbers, so the server re-checks the lap
 * against the recorded ghost and the game's physics limits (the same GameConstants the game uses).
 *
 * Rules run cheapest first and the first failure decides the reason. Each rule method returns a
 * description of the problem, or null if the rule passes.
 */
@Component
public class LapValidator {

    /** Allowed difference between the actual and expected number of ghost samples. */
    static final int SAMPLE_COUNT_TOLERANCE = 2;
    /** 10% headroom over MAX_SPEED for float rounding and drift. */
    static final float SPEED_TOLERANCE = 1.1f;

    public ValidationResult validate(LapSubmissionRequest lap, Track track) {
        String problem = tooFast(lap, track);
        if (problem != null) {
            return ValidationResult.rejected(TOO_FAST, problem);
        }
        problem = checkpointOrder(lap);
        if (problem != null) {
            return ValidationResult.rejected(CHECKPOINT_ORDER, problem);
        }

        byte[] ghostBytes;
        Ghost ghost;
        try {
            ghostBytes = Base64.getDecoder().decode(lap.ghost());
            ghost = GhostCodec.decode(ghostBytes);
        } catch (IllegalArgumentException | GhostFormatException e) { // bad base64 | bad ghost data
            return ValidationResult.rejected(BAD_GHOST, e.getMessage());
        }
        problem = ghostDoesNotMatchLap(lap, ghost);
        if (problem != null) {
            return ValidationResult.rejected(BAD_GHOST, problem);
        }

        problem = sampleCountMismatch(lap, ghost);
        if (problem != null) {
            return ValidationResult.rejected(SAMPLE_COUNT_MISMATCH, problem);
        }
        problem = speedExceeded(ghost);
        if (problem != null) {
            return ValidationResult.rejected(SPEED_EXCEEDED, problem);
        }
        return ValidationResult.accepted(ghostBytes);
    }

    /** TOO_FAST: quicker than the track's minimum plausible time. */
    String tooFast(LapSubmissionRequest lap, Track track) {
        if (lap.lapTimeMs() < track.minLapTimeMs()) {
            return "lap time " + lap.lapTimeMs() + " ms is below the track minimum of " + track.minLapTimeMs() + " ms";
        }
        return null;
    }

    /** CHECKPOINT_ORDER: one time per checkpoint, strictly increasing, ending exactly at the lap time. */
    String checkpointOrder(LapSubmissionRequest lap) {
        List<Integer> times = lap.checkpointTimesMs();
        if (times.size() != GameConstants.CHECKPOINT_COUNT) {
            return "expected " + GameConstants.CHECKPOINT_COUNT + " checkpoint times, got " + times.size();
        }
        int previous = 0; // the lap starts at 0 ms
        for (int i = 0; i < times.size(); i++) {
            if (times.get(i) <= previous) {
                return "checkpoint " + i + " time " + times.get(i) + " ms is not after " + previous + " ms";
            }
            previous = times.get(i);
        }
        if (previous != lap.lapTimeMs()) {
            return "last checkpoint time " + previous + " ms does not equal the lap time " + lap.lapTimeMs() + " ms";
        }
        return null;
    }

    /** BAD_GHOST (part 2): the ghost decoded fine but belongs to a different lap, or uses the wrong rate. */
    String ghostDoesNotMatchLap(LapSubmissionRequest lap, Ghost ghost) {
        if (ghost.trackId() != lap.trackId()) {
            return "ghost is for track " + ghost.trackId() + ", lap is for track " + lap.trackId();
        }
        if (ghost.lapTimeMs() != lap.lapTimeMs()) {
            return "ghost lap time " + ghost.lapTimeMs() + " ms does not match " + lap.lapTimeMs() + " ms";
        }
        // A lower rate would hide movement between samples from the speed check.
        if (ghost.sampleRateHz() != GameConstants.GHOST_SAMPLE_RATE_HZ) {
            return "ghost sample rate must be " + GameConstants.GHOST_SAMPLE_RATE_HZ + " Hz, got " + ghost.sampleRateHz();
        }
        return null;
    }

    /** SAMPLE_COUNT_MISMATCH: a 15 s lap at 30 Hz must have about 450 samples. */
    String sampleCountMismatch(LapSubmissionRequest lap, Ghost ghost) {
        double expected = lap.lapTimeMs() * ghost.sampleRateHz() / 1000.0;
        if (Math.abs(ghost.sampleCount() - expected) > SAMPLE_COUNT_TOLERANCE) {
            return String.format("ghost has %d samples, expected %.1f (+-%d)", ghost.sampleCount(), expected, SAMPLE_COUNT_TOLERANCE);
        }
        return null;
    }

    /** SPEED_EXCEEDED: distance between consecutive samples divided by the time between them. */
    String speedExceeded(Ghost ghost) {
        double maxSpeed = GameConstants.MAX_SPEED * SPEED_TOLERANCE;
        double secondsPerSample = 1.0 / ghost.sampleRateHz();
        List<GhostSample> samples = ghost.samples();
        for (int i = 1; i < samples.size(); i++) {
            GhostSample a = samples.get(i - 1);
            GhostSample b = samples.get(i);
            double speed = Math.hypot(b.x() - a.x(), b.y() - a.y()) / secondsPerSample;
            if (speed > maxSpeed) {
                return String.format("speed %.0f units/s between samples %d and %d is above the limit of %.0f",
                        speed, i - 1, i, maxSpeed);
            }
        }
        return null;
    }
}
