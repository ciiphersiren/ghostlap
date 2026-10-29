package com.ghostlap.race;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ghostlap.GameConstants;
import com.ghostlap.car.Car;
import com.ghostlap.car.CarInput;
import com.ghostlap.track.Track;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check without a window: a simple autopilot drives the real car physics around the
 * real track, using the same tick order as RaceScreen, and must complete valid laps.
 */
class LapSimulationTest {

    private static final int LOOK_AHEAD_SAMPLES = 8;
    private static final float CORNER_SPEED = 330f;

    @Test
    void autopilotCompletesValidLaps() {
        Track track = Track.createDefault();
        Car car = new Car();
        CarInput input = new CarInput();
        LapTimer timer = new LapTimer(GameConstants.CHECKPOINT_COUNT);
        car.reset(track.getStartPosition(), track.getStartHeading());

        int maxTicks = 3 * 60 * GameConstants.PHYSICS_HZ; // 3 minutes of game time
        int offTrackTicks = 0;
        for (int tick = 0; tick < maxTicks && timer.getCompletedLaps() < 2; tick++) {
            steerTowardsTrack(track, car, input);

            car.savePreviousState();
            boolean onTrack = track.isOnTrack(car.position);
            if (!onTrack) {
                offTrackTicks++;
            }
            car.update(input, onTrack, GameConstants.PHYSICS_STEP);
            timer.tick();
            int crossed = track.findCrossedCheckpoint(car.previousPosition, car.position);
            if (crossed >= 0) {
                timer.onCheckpointCrossed(crossed);
            }
        }

        assertThat(timer.getCompletedLaps()).isEqualTo(2);
        assertThat(offTrackTicks).isZero();
        // A lap can't be faster than driving the whole centerline at top speed.
        float fastestPossibleMs = track.getLength() / GameConstants.MAX_SPEED * 1000f;
        assertThat(timer.getBestLapMs()).isGreaterThan((int) fastestPossibleMs);
        System.out.printf("Autopilot best lap: %s (track length %.0f units)%n",
                LapTimer.formatTime(timer.getBestLapMs()), track.getLength());
    }

    /** Aims at a centerline point a few samples ahead and slows down when it needs to turn a lot. */
    private static void steerTowardsTrack(Track track, Car car, CarInput input) {
        Vector2[] center = track.getCenterline();
        int nearest = 0;
        for (int i = 1; i < center.length; i++) {
            if (center[i].dst2(car.position) < center[nearest].dst2(car.position)) {
                nearest = i;
            }
        }
        Vector2 target = center[(nearest + LOOK_AHEAD_SAMPLES) % center.length];
        float wanted = MathUtils.atan2(target.y - car.position.y, target.x - car.position.x);
        float error = wanted - car.heading;
        error = MathUtils.atan2(MathUtils.sin(error), MathUtils.cos(error)); // shortest way round

        input.clear();
        input.steer = MathUtils.clamp(error * 4f, -1f, 1f);
        boolean sharpTurn = Math.abs(error) > 0.25f;
        float speed = car.getSpeed();
        input.throttle = !sharpTurn || speed < CORNER_SPEED;
        input.brake = sharpTurn && speed > CORNER_SPEED + 80f;
    }
}
