package com.ghostlap.ghost;

import com.badlogic.gdx.math.Vector2;
import com.ghostlap.car.Car;
import com.ghostlap.shared.GameConstants;
import com.ghostlap.shared.ghost.Ghost;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GhostRecorderTest {

    @Test
    void recordsEverySecondTickPlusTheStart() {
        Car car = new Car();
        car.reset(new Vector2(0, 0), 0f);
        GhostRecorder recorder = new GhostRecorder();

        recorder.start(car);
        for (int tick = 1; tick <= 10; tick++) {
            car.position.x = tick; // pretend the car moves 1 unit per tick
            recorder.onTick(car);
        }
        Ghost ghost = recorder.finish(167);

        // Ticks 0, 2, 4, 6, 8, 10
        assertThat(ghost.sampleCount()).isEqualTo(6);
        assertThat(ghost.samples()).extracting(s -> s.x()).containsExactly(0f, 2f, 4f, 6f, 8f, 10f);
        assertThat(ghost.sampleRateHz()).isEqualTo(GameConstants.GHOST_SAMPLE_RATE_HZ);
        assertThat(ghost.trackId()).isEqualTo(GameConstants.TRACK_ID);
        assertThat(ghost.lapTimeMs()).isEqualTo(167);
    }

    @Test
    void ignoresTicksWhenNotRecording() {
        Car car = new Car();
        GhostRecorder recorder = new GhostRecorder();

        recorder.onTick(car);
        recorder.onTick(car);

        assertThat(recorder.getSampleCount()).isZero();
    }

    @Test
    void finishedGhostIsNotChangedByTheNextRecording() {
        Car car = new Car();
        GhostRecorder recorder = new GhostRecorder();
        recorder.start(car);
        Ghost first = recorder.finish(0);

        recorder.start(car);
        recorder.onTick(car);
        recorder.onTick(car);

        assertThat(first.sampleCount()).isEqualTo(1);
    }
}
