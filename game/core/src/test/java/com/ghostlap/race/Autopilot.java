package com.ghostlap.race;

import com.ghostlap.car.AutoDriver;
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
            AutoDriver.steer(track, car, input);

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
}
