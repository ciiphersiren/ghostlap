package com.ghostlap.race.validation;

import com.ghostlap.race.lap.LapSubmissionRequest;
import com.ghostlap.race.lap.RejectReason;
import com.ghostlap.race.track.Track;
import com.ghostlap.shared.GameConstants;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostSample;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.ghostlap.race.TestLaps.checkpointTimes;
import static com.ghostlap.race.TestLaps.circleGhost;
import static com.ghostlap.race.TestLaps.expectedSamples;
import static com.ghostlap.race.TestLaps.validLap;
import static com.ghostlap.race.TestLaps.withGhost;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests (no Spring, no database): one nested group per validation rule. */
class LapValidatorTest {

    private static final int LAP_MS = 16_000;
    private final Track track = new Track(GameConstants.TRACK_ID, "Test track", 13_000);
    private final LapValidator validator = new LapValidator();

    private ValidationResult validate(LapSubmissionRequest lap) {
        return validator.validate(lap, track);
    }

    @Test
    void believableLapIsAccepted() {
        ValidationResult result = validate(validLap(LAP_MS));

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.ghostBytes()).isNotEmpty();
    }

    @Nested
    class TooFast {

        @Test
        void lapBelowTrackMinimumIsRejected() {
            assertThat(validate(validLap(12_999)).reason()).isEqualTo(RejectReason.TOO_FAST);
        }

        @Test
        void lapExactlyAtTrackMinimumIsAccepted() {
            assertThat(validate(validLap(13_000)).isAccepted()).isTrue();
        }
    }

    @Nested
    class CheckpointOrder {

        @Test
        void wrongNumberOfCheckpointsIsRejected() {
            List<Integer> sevenTimes = checkpointTimes(LAP_MS).subList(1, 8);
            assertThat(validate(withCheckpoints(sevenTimes)).reason()).isEqualTo(RejectReason.CHECKPOINT_ORDER);
        }

        @Test
        void timesThatGoBackwardsAreRejected() {
            List<Integer> times = new ArrayList<>(checkpointTimes(LAP_MS));
            times.set(3, times.get(1)); // checkpoint 3 "before" checkpoint 2
            assertThat(validate(withCheckpoints(times)).reason()).isEqualTo(RejectReason.CHECKPOINT_ORDER);
        }

        @Test
        void equalTimesAreRejected() {
            List<Integer> times = new ArrayList<>(checkpointTimes(LAP_MS));
            times.set(2, times.get(1)); // must be strictly increasing
            assertThat(validate(withCheckpoints(times)).reason()).isEqualTo(RejectReason.CHECKPOINT_ORDER);
        }

        @Test
        void zeroOrNegativeFirstTimeIsRejected() {
            List<Integer> times = new ArrayList<>(checkpointTimes(LAP_MS));
            times.set(0, 0);
            assertThat(validate(withCheckpoints(times)).reason()).isEqualTo(RejectReason.CHECKPOINT_ORDER);
        }

        @Test
        void lastTimeMustEqualLapTime() {
            List<Integer> times = new ArrayList<>(checkpointTimes(LAP_MS));
            times.set(7, LAP_MS - 1);
            assertThat(validate(withCheckpoints(times)).reason()).isEqualTo(RejectReason.CHECKPOINT_ORDER);
        }

        private LapSubmissionRequest withCheckpoints(List<Integer> times) {
            LapSubmissionRequest lap = validLap(LAP_MS);
            return new LapSubmissionRequest(lap.trackId(), lap.lapTimeMs(), times, lap.ghost());
        }
    }

    @Nested
    class BadGhost {

        @Test
        void notBase64IsRejected() {
            assertThat(validate(withGhostText("this is not base64!")).reason()).isEqualTo(RejectReason.BAD_GHOST);
        }

        @Test
        void base64ThatIsNotAGhostIsRejected() {
            String garbage = Base64.getEncoder().encodeToString("hello".getBytes());
            assertThat(validate(withGhostText(garbage)).reason()).isEqualTo(RejectReason.BAD_GHOST);
        }

        @Test
        void ghostForAnotherTrackIsRejected() {
            Ghost circle = circleGhost(LAP_MS, expectedSamples(LAP_MS));
            Ghost otherTrack = new Ghost(2, circle.sampleRateHz(), circle.lapTimeMs(), circle.samples());
            assertThat(validate(withGhost(validLap(LAP_MS), otherTrack)).reason()).isEqualTo(RejectReason.BAD_GHOST);
        }

        @Test
        void ghostWithDifferentLapTimeIsRejected() {
            Ghost circle = circleGhost(LAP_MS, expectedSamples(LAP_MS));
            Ghost otherTime = new Ghost(circle.trackId(), circle.sampleRateHz(), LAP_MS - 500, circle.samples());
            assertThat(validate(withGhost(validLap(LAP_MS), otherTime)).reason()).isEqualTo(RejectReason.BAD_GHOST);
        }

        @Test
        void ghostWithWrongSampleRateIsRejected() {
            Ghost circle = circleGhost(LAP_MS, expectedSamples(LAP_MS));
            Ghost oneHz = new Ghost(circle.trackId(), 1, circle.lapTimeMs(), circle.samples());
            assertThat(validate(withGhost(validLap(LAP_MS), oneHz)).reason()).isEqualTo(RejectReason.BAD_GHOST);
        }

        private LapSubmissionRequest withGhostText(String ghost) {
            LapSubmissionRequest lap = validLap(LAP_MS);
            return new LapSubmissionRequest(lap.trackId(), lap.lapTimeMs(), lap.checkpointTimesMs(), ghost);
        }
    }

    @Nested
    class SampleCountMismatch {

        // 16 000 ms at 30 Hz = 480 expected samples, so 478..482 are allowed.

        @Test
        void twoExtraSamplesAreAllowed() {
            assertThat(validate(withSampleCount(482)).isAccepted()).isTrue();
        }

        @Test
        void threeExtraSamplesAreRejected() {
            assertThat(validate(withSampleCount(483)).reason()).isEqualTo(RejectReason.SAMPLE_COUNT_MISMATCH);
        }

        @Test
        void threeMissingSamplesAreRejected() {
            assertThat(validate(withSampleCount(477)).reason()).isEqualTo(RejectReason.SAMPLE_COUNT_MISMATCH);
        }

        @Test
        void halfTheSamplesAreRejected() {
            assertThat(validate(withSampleCount(240)).reason()).isEqualTo(RejectReason.SAMPLE_COUNT_MISMATCH);
        }

        private LapSubmissionRequest withSampleCount(int count) {
            return withGhost(validLap(LAP_MS), circleGhost(LAP_MS, count));
        }
    }

    @Nested
    class SpeedExceeded {

        @Test
        void teleportBetweenTwoSamplesIsRejected() {
            Ghost circle = circleGhost(LAP_MS, expectedSamples(LAP_MS));
            List<GhostSample> samples = new ArrayList<>(circle.samples());
            GhostSample s = samples.get(100);
            samples.set(100, new GhostSample(s.x() + 500, s.y(), s.heading())); // 500 units in 1/30 s

            LapSubmissionRequest lap = withGhost(validLap(LAP_MS), withSamples(circle, samples));

            assertThat(validate(lap).reason()).isEqualTo(RejectReason.SPEED_EXCEEDED);
        }

        @Test
        void speedJustUnderTheToleranceIsAllowed() {
            // 650 units/s: above MAX_SPEED (600) but inside the 10% tolerance (660).
            assertThat(validate(straightLineAt(650f)).isAccepted()).isTrue();
        }

        @Test
        void speedJustOverTheToleranceIsRejected() {
            assertThat(validate(straightLineAt(670f)).reason()).isEqualTo(RejectReason.SPEED_EXCEEDED);
        }

        /** A ghost driving in a straight line at a constant speed. */
        private LapSubmissionRequest straightLineAt(float speed) {
            List<GhostSample> samples = new ArrayList<>();
            for (int i = 0; i < expectedSamples(LAP_MS); i++) {
                samples.add(new GhostSample(speed * i / GameConstants.GHOST_SAMPLE_RATE_HZ, 0f, 0f));
            }
            return withGhost(validLap(LAP_MS), withSamples(circleGhost(LAP_MS, 1), samples));
        }

        private Ghost withSamples(Ghost ghost, List<GhostSample> samples) {
            return new Ghost(ghost.trackId(), ghost.sampleRateHz(), ghost.lapTimeMs(), samples);
        }
    }
}
