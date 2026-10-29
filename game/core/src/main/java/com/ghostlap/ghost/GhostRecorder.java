package com.ghostlap.ghost;

import com.ghostlap.car.Car;
import com.ghostlap.shared.GameConstants;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostSample;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the car during a lap. Physics runs at 60 Hz but we keep only every 2nd tick (30 Hz):
 * sample {@code i} is the car's state at lap tick {@code 2 * i}.
 */
public class GhostRecorder {

    private final List<GhostSample> samples = new ArrayList<>();
    private int ticks;
    private boolean recording;

    /** Call on the tick the car crosses the start line. Records sample 0. */
    public void start(Car car) {
        samples.clear();
        ticks = 0;
        recording = true;
        addSample(car);
    }

    /** Call once per physics tick while a lap is running, after the car has moved. */
    public void onTick(Car car) {
        if (!recording) {
            return;
        }
        ticks++;
        if (ticks % GameConstants.GHOST_SAMPLE_EVERY_TICKS == 0) {
            addSample(car);
        }
    }

    /** Call on the tick the lap is completed. Returns the recorded lap and stops recording. */
    public Ghost finish(int lapTimeMs) {
        recording = false;
        return new Ghost(GameConstants.TRACK_ID, GameConstants.GHOST_SAMPLE_RATE_HZ, lapTimeMs, samples);
    }

    public void stop() {
        recording = false;
        samples.clear();
    }

    public int getSampleCount() {
        return samples.size();
    }

    private void addSample(Car car) {
        samples.add(new GhostSample(car.position.x, car.position.y, car.heading));
    }
}
