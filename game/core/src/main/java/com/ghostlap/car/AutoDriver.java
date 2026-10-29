package com.ghostlap.car;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ghostlap.track.Track;

/**
 * A simple autopilot: aims at a centerline point a few samples ahead and slows down for sharp
 * turns. Used by the tests (headless laps) and by benchmark mode (repeatable driving).
 */
public final class AutoDriver {

    private static final int LOOK_AHEAD_SAMPLES = 8;
    private static final float CORNER_SPEED = 330f;

    private AutoDriver() {
    }

    public static void steer(Track track, Car car, CarInput input) {
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
