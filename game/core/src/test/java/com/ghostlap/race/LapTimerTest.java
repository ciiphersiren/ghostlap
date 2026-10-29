package com.ghostlap.race;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LapTimerTest {

    private static final int CHECKPOINTS = 8;
    private LapTimer timer;

    @BeforeEach
    void setUp() {
        timer = new LapTimer(CHECKPOINTS);
    }

    /** Crosses checkpoints 1..7 then 0, ticking {@code ticksPerSegment} between each. */
    private void driveFullLap(int ticksPerSegment) {
        for (int cp = 1; cp <= CHECKPOINTS; cp++) {
            tick(ticksPerSegment);
            timer.onCheckpointCrossed(cp % CHECKPOINTS);
        }
    }

    private void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            timer.tick();
        }
    }

    @Test
    void lapStartsOnlyWhenStartLineIsCrossed() {
        tick(100);
        assertThat(timer.isLapRunning()).isFalse();
        assertThat(timer.getCurrentLapMs()).isEqualTo(LapTimer.NO_TIME);

        assertThat(timer.onCheckpointCrossed(0)).isEqualTo(LapTimer.CrossingResult.LAP_STARTED);
        assertThat(timer.isLapRunning()).isTrue();
        assertThat(timer.getNextCheckpoint()).isEqualTo(1);
    }

    @Test
    void reportsWhatEachCrossingDid() {
        timer.onCheckpointCrossed(0);
        assertThat(timer.onCheckpointCrossed(1)).isEqualTo(LapTimer.CrossingResult.CHECKPOINT);
        assertThat(timer.onCheckpointCrossed(5)).isEqualTo(LapTimer.CrossingResult.IGNORED);
        for (int cp = 2; cp < CHECKPOINTS; cp++) {
            timer.onCheckpointCrossed(cp);
        }
        assertThat(timer.onCheckpointCrossed(0)).isEqualTo(LapTimer.CrossingResult.LAP_COMPLETED);
    }

    @Test
    void checkpointsBeforeStartAreIgnored() {
        assertThat(timer.onCheckpointCrossed(3)).isEqualTo(LapTimer.CrossingResult.IGNORED);
        assertThat(timer.isLapRunning()).isFalse();
        assertThat(timer.getNextCheckpoint()).isZero();
    }

    @Test
    void orderedLapIsTimedFromTicks() {
        timer.onCheckpointCrossed(0);
        driveFullLap(75); // 8 segments * 75 ticks = 600 ticks = 10 s at 60 Hz

        assertThat(timer.getCompletedLaps()).isEqualTo(1);
        assertThat(timer.getLastLapMs()).isEqualTo(10_000);
        assertThat(timer.getBestLapMs()).isEqualTo(10_000);
        // The next lap starts immediately at 0.
        assertThat(timer.getCurrentLapMs()).isZero();
    }

    @Test
    void skippingACheckpointDoesNotFinishTheLap() {
        timer.onCheckpointCrossed(0);
        for (int cp = 1; cp < CHECKPOINTS; cp++) {
            if (cp == 4) {
                continue; // cut the track and miss checkpoint 4
            }
            tick(10);
            timer.onCheckpointCrossed(cp);
        }
        timer.onCheckpointCrossed(0);

        assertThat(timer.getCompletedLaps()).isZero();
        assertThat(timer.getLastLapMs()).isEqualTo(LapTimer.NO_TIME);
        assertThat(timer.getNextCheckpoint()).isEqualTo(4);
    }

    @Test
    void bestLapKeepsTheFastestTime() {
        timer.onCheckpointCrossed(0);
        driveFullLap(90);  // 12 s
        driveFullLap(60);  // 8 s
        driveFullLap(120); // 16 s

        assertThat(timer.getCompletedLaps()).isEqualTo(3);
        assertThat(timer.getLastLapMs()).isEqualTo(16_000);
        assertThat(timer.getBestLapMs()).isEqualTo(8_000);
    }

    @Test
    void recordsCheckpointSplitsEndingWithTheLapTime() {
        assertThat(timer.getLastLapCheckpointTimesMs()).isEmpty();

        timer.onCheckpointCrossed(0);
        driveFullLap(75); // a checkpoint every 75 ticks = 1250 ms

        assertThat(timer.getLastLapCheckpointTimesMs())
                .containsExactly(1250, 2500, 3750, 5000, 6250, 7500, 8750, 10_000)
                .endsWith(timer.getLastLapMs());
    }

    @Test
    void splitsBelongToTheLastCompletedLap() {
        timer.onCheckpointCrossed(0);
        driveFullLap(75);
        tick(10);
        timer.onCheckpointCrossed(1); // the next lap is under way

        assertThat(timer.getLastLapCheckpointTimesMs()).hasSize(CHECKPOINTS).endsWith(10_000);
    }

    @Test
    void formatsTimes() {
        assertThat(LapTimer.formatTime(83_456)).isEqualTo("01:23.456");
        assertThat(LapTimer.formatTime(LapTimer.NO_TIME)).isEqualTo("--:--.---");
    }

    @Test
    void formatsTimesWithLeadingZeros() {
        assertThat(LapTimer.formatTime(0)).isEqualTo("00:00.000");
        assertThat(LapTimer.formatTime(5_007)).isEqualTo("00:05.007");
        assertThat(LapTimer.formatTime(15_333)).isEqualTo("00:15.333");
        assertThat(LapTimer.formatTime(60_050)).isEqualTo("01:00.050");
        assertThat(LapTimer.formatTime(754_999)).isEqualTo("12:34.999");
    }

    @Test
    void appendsToAnExistingBuilder() {
        StringBuilder text = new StringBuilder("LAP ");

        LapTimer.appendTime(text, 15_333);

        assertThat(text).hasToString("LAP 00:15.333");
    }
}
