package com.ghostlap;

/**
 * Tuning values for the simulation. Units: 1 world unit is roughly 1 pixel at default zoom.
 * In Phase 2 these move to shared/ghost-format so the server can validate laps with the same numbers.
 */
public final class GameConstants {

    private GameConstants() {
    }

    // Fixed-timestep physics
    public static final int PHYSICS_HZ = 60;
    public static final float PHYSICS_STEP = 1f / PHYSICS_HZ;

    // Car
    public static final float MAX_SPEED = 600f;             // units per second
    public static final float MAX_REVERSE_SPEED = 150f;
    public static final float ACCELERATION = 420f;          // units per second^2
    public static final float BRAKE_DECELERATION = 900f;
    public static final float REVERSE_ACCELERATION = 250f;
    public static final float ON_TRACK_DRAG = 0.4f;         // fraction of forward speed lost per second
    public static final float OFF_TRACK_DRAG = 3.0f;
    public static final float LATERAL_GRIP = 0.25f;         // fraction of sideways speed removed each tick (1 = no drift)
    public static final float TURN_RATE = 3.0f;             // radians per second at full steering lock
    public static final float FULL_STEER_SPEED = 150f;      // below this speed, steering is weaker
    public static final float CAR_LENGTH = 44f;
    public static final float CAR_WIDTH = 22f;

    // Track
    public static final float TRACK_WIDTH = 150f;
    public static final int CHECKPOINT_COUNT = 8;
}
