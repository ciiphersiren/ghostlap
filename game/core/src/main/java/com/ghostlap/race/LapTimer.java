package com.ghostlap.race;

import com.ghostlap.shared.GameConstants;

/**
 * Counts physics ticks to time laps. A lap starts when the car crosses checkpoint 0 (start/finish)
 * and only finishes after checkpoints 1, 2, ... N-1 were crossed in order and the car crosses 0 again.
 * Crossing any checkpoint out of order is ignored, so cutting the track never completes a lap.
 */
public class LapTimer {

    public static final int NO_TIME = -1;

    private final int checkpointCount;
    private boolean lapRunning;
    private int nextCheckpoint;
    private int lapTicks;
    private int lastLapTicks;
    private int bestLapTicks;
    private int completedLaps;

    public LapTimer(int checkpointCount) {
        this.checkpointCount = checkpointCount;
        reset();
    }

    public void reset() {
        lapRunning = false;
        nextCheckpoint = 0;
        lapTicks = 0;
        lastLapTicks = NO_TIME;
        bestLapTicks = NO_TIME;
        completedLaps = 0;
    }

    /** Call once per physics tick. Timing in ticks (not seconds) keeps it exact and deterministic. */
    public void tick() {
        if (lapRunning) {
            lapTicks++;
        }
    }

    /** What crossing a checkpoint did, so the race screen can start/stop ghost recording. */
    public enum CrossingResult {
        IGNORED,        // wrong order
        CHECKPOINT,     // an intermediate checkpoint
        LAP_STARTED,    // first crossing of the start line
        LAP_COMPLETED   // finished a lap; the next lap starts at the same moment
    }

    public CrossingResult onCheckpointCrossed(int index) {
        if (index != nextCheckpoint) {
            return CrossingResult.IGNORED;
        }
        nextCheckpoint = (index + 1) % checkpointCount;
        if (index != 0) {
            return CrossingResult.CHECKPOINT;
        }
        boolean finished = lapRunning;
        if (finished) {
            finishLap();
        }
        lapRunning = true;
        lapTicks = 0;
        return finished ? CrossingResult.LAP_COMPLETED : CrossingResult.LAP_STARTED;
    }

    private void finishLap() {
        lastLapTicks = lapTicks;
        if (bestLapTicks == NO_TIME || lapTicks < bestLapTicks) {
            bestLapTicks = lapTicks;
        }
        completedLaps++;
    }

    public boolean isLapRunning() {
        return lapRunning;
    }

    /** Physics ticks since the start line was crossed (0 when no lap is running). */
    public int getLapTicks() {
        return lapTicks;
    }

    public int getNextCheckpoint() {
        return nextCheckpoint;
    }

    public int getCheckpointCount() {
        return checkpointCount;
    }

    public int getCompletedLaps() {
        return completedLaps;
    }

    public int getCurrentLapMs() {
        return lapRunning ? ticksToMs(lapTicks) : NO_TIME;
    }

    public int getLastLapMs() {
        return ticksToMs(lastLapTicks);
    }

    public int getBestLapMs() {
        return ticksToMs(bestLapTicks);
    }

    static int ticksToMs(int ticks) {
        if (ticks == NO_TIME) {
            return NO_TIME;
        }
        return Math.round(ticks * 1000f / GameConstants.PHYSICS_HZ);
    }

    /** Formats milliseconds as mm:ss.mmm, or dashes when there is no time yet. */
    public static String formatTime(int ms) {
        if (ms == NO_TIME) {
            return "--:--.---";
        }
        return String.format("%02d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000);
    }
}
