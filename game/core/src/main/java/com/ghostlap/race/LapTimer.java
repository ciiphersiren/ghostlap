package com.ghostlap.race;

import com.ghostlap.GameConstants;

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

    public void onCheckpointCrossed(int index) {
        if (index != nextCheckpoint) {
            return; // wrong order: ignore
        }
        if (index == 0) {
            if (lapRunning) {
                finishLap();
            }
            lapRunning = true;
            lapTicks = 0;
        }
        nextCheckpoint = (index + 1) % checkpointCount;
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
