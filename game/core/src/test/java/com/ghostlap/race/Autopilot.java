package com.ghostlap.race;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ghostlap.car.Car;
import com.ghostlap.car.CarInput;
import com.ghostlap.ghost.GhostRecorder;
import com.ghostlap.shared.GameConstants;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.track.Track;

import java.util.ArrayList;
import java.util.List;

/**
 * Test helper: drives the real car physics around the real track, with the same tick order as
 * RaceScreen, and returns each completed lap exactly as the game would upload it.
 */
public final class Autopilot {

    private static final int LOOK_AHEAD_SAMPLES = 8;
    private static final float CORNER_SPEED = 330f;

    /** A completed lap: what RaceScreen puts in a LapUpload. */
    public record DrivenLap(int lapTimeMs, int[] checkpointTimesMs, Ghost ghost) {
    }

    /** The result of a drive. */
    public record Run(List<DrivenLap> laps, int offTrackTicks, LapTimer timer, Track track) {
    }

    private Autopilot() {
    }

    public static Run drive(int lapCount) {
        Track track = Track.createDefault();
        Car car = new Car();
        CarInput input = new CarInput();
        LapTimer timer = new LapTimer(GameConstants.CHECKPOINT_COUNT);
        GhostRecorder recorder = new GhostRecorder();
        car.reset(track.getStartPosition(), track.getStartHeading());

        List<DrivenLap> laps = new ArrayList<>();
        int maxTicks = 3 * 60 * GameConstants.PHYSICS_HZ; // 3 minutes of game time
        int offTrackTicks = 0;
        for (int tick = 0; tick < maxTicks && laps.size() < lapCount; tick++) {
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
                    int lapMs = timer.getLastLapMs();
                    laps.add(new DrivenLap(lapMs, timer.getLastLapCheckpointTimesMs(), recorder.finish(lapMs)));
                }
                if (result == LapTimer.CrossingResult.LAP_COMPLETED || result == LapTimer.CrossingResult.LAP_STARTED) {
                    recorder.start(car);
                }
            }
        }
        return new Run(laps, offTrackTicks, timer, track);
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
