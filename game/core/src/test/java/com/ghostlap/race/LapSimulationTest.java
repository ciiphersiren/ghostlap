package com.ghostlap.race;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ghostlap.car.Car;
import com.ghostlap.car.CarInput;
import com.ghostlap.ghost.GhostRecorder;
import com.ghostlap.shared.GameConstants;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostCodec;
import com.ghostlap.shared.ghost.GhostSample;
import com.ghostlap.track.Track;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check without a window: a simple autopilot drives the real car physics around the
 * real track, using the same tick order as RaceScreen, and must complete valid laps. The recorded
 * ghost must also be one the server will accept.
 */
class LapSimulationTest {

    private static final int LOOK_AHEAD_SAMPLES = 8;
    private static final float CORNER_SPEED = 330f;

    @Test
    void autopilotCompletesValidLaps() throws IOException {
        Track track = Track.createDefault();
        Car car = new Car();
        CarInput input = new CarInput();
        LapTimer timer = new LapTimer(GameConstants.CHECKPOINT_COUNT);
        GhostRecorder recorder = new GhostRecorder();
        car.reset(track.getStartPosition(), track.getStartHeading());

        int maxTicks = 3 * 60 * GameConstants.PHYSICS_HZ; // 3 minutes of game time
        int offTrackTicks = 0;
        Ghost lastGhost = null;
        for (int tick = 0; tick < maxTicks && timer.getCompletedLaps() < 2; tick++) {
            steerTowardsTrack(track, car, input);

            car.savePreviousState();
            boolean onTrack = track.isOnTrack(car.position);
            if (!onTrack) {
                offTrackTicks++;
            }
            car.update(input, onTrack, GameConstants.PHYSICS_STEP);
            timer.tick();
            recorder.onTick(car);
            int crossed = track.findCrossedCheckpoint(car.previousPosition, car.position);
            if (crossed >= 0) {
                LapTimer.CrossingResult result = timer.onCheckpointCrossed(crossed);
                if (result == LapTimer.CrossingResult.LAP_COMPLETED) {
                    lastGhost = recorder.finish(timer.getLastLapMs());
                }
                if (result == LapTimer.CrossingResult.LAP_COMPLETED || result == LapTimer.CrossingResult.LAP_STARTED) {
                    recorder.start(car);
                }
            }
        }

        assertThat(timer.getCompletedLaps()).isEqualTo(2);
        assertGhostIsValid(lastGhost, timer.getLastLapMs());
        assertThat(offTrackTicks).isZero();
        // A lap can't be faster than driving the whole centerline at top speed.
        float fastestPossibleMs = track.getLength() / GameConstants.MAX_SPEED * 1000f;
        assertThat(timer.getBestLapMs()).isGreaterThan((int) fastestPossibleMs);
        System.out.printf("Autopilot best lap: %s (track length %.0f units)%n",
                LapTimer.formatTime(timer.getBestLapMs()), track.getLength());
    }

    /** The checks race-service will run in Phase 3, applied to a ghost from real driving. */
    private static void assertGhostIsValid(Ghost ghost, int lapTimeMs) throws IOException {
        assertThat(ghost).isNotNull();
        assertThat(ghost.lapTimeMs()).isEqualTo(lapTimeMs);

        // SAMPLE_COUNT_MISMATCH: count must be lapTimeMs x 30 Hz, +-2
        float expectedSamples = lapTimeMs * GameConstants.GHOST_SAMPLE_RATE_HZ / 1000f;
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
