package com.ghostlap.car;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import static com.ghostlap.shared.GameConstants.*;

/**
 * Arcade top-down car. Each physics tick the velocity is split into a forward part (along the
 * heading) and a lateral part (sideways). Engine, brakes and drag act on the forward part;
 * grip removes most of the lateral part, and what is left over is the drift.
 */
public class Car {

    public final Vector2 position = new Vector2();
    public final Vector2 velocity = new Vector2();
    /** Direction the car is pointing, in radians. 0 = +x (right), counter-clockwise is positive. */
    public float heading;

    // State from the previous tick, used to interpolate rendering between ticks.
    public final Vector2 previousPosition = new Vector2();
    public float previousHeading;

    // Reused every tick so the physics step does not allocate.
    private final Vector2 forward = new Vector2();
    private final Vector2 lateral = new Vector2();

    public void reset(Vector2 startPosition, float startHeading) {
        position.set(startPosition);
        velocity.setZero();
        heading = startHeading;
        savePreviousState();
    }

    public void savePreviousState() {
        previousPosition.set(position);
        previousHeading = heading;
    }

    /** Advances the car by one fixed timestep {@code dt}. */
    public void update(CarInput input, boolean onTrack, float dt) {
        // 1. Unit vectors for "ahead" and "to the left" of the car.
        forward.set((float) Math.cos(heading), (float) Math.sin(heading));
        lateral.set(-forward.y, forward.x);

        // 2. Project velocity onto them (dot product = how much of the velocity points that way).
        float forwardSpeed = velocity.dot(forward);
        float lateralSpeed = velocity.dot(lateral);

        // 3. Engine and brakes change forward speed only.
        if (input.throttle) {
            forwardSpeed += ACCELERATION * dt;
        }
        if (input.brake) {
            if (forwardSpeed > 0f) {
                forwardSpeed = Math.max(0f, forwardSpeed - BRAKE_DECELERATION * dt);
            } else {
                forwardSpeed -= REVERSE_ACCELERATION * dt;
            }
        }

        // 4. Drag: a percentage of speed lost per second. Off-track drag is much stronger.
        float drag = onTrack ? ON_TRACK_DRAG : OFF_TRACK_DRAG;
        forwardSpeed -= forwardSpeed * drag * dt;
        if (!input.throttle && !input.brake && Math.abs(forwardSpeed) < 1f) {
            forwardSpeed = 0f; // stop creeping forever
        }
        forwardSpeed = MathUtils.clamp(forwardSpeed, -MAX_REVERSE_SPEED, MAX_SPEED);

        // 5. Grip: remove part of the sideways speed. Less than 100% grip leaves a small drift.
        lateralSpeed *= (1f - LATERAL_GRIP);

        // 6. Steering. Turning needs speed (a parked car can't spin), and it flips when reversing.
        float steerStrength = MathUtils.clamp(forwardSpeed / FULL_STEER_SPEED, -1f, 1f);
        heading += input.steer * TURN_RATE * steerStrength * dt;
        heading = wrapAngle(heading);

        // 7. Rebuild velocity from the two parts (using the old heading: next tick, part of it
        //    becomes lateral relative to the new heading, and grip eats it, which is the drift).
        velocity.set(forward).scl(forwardSpeed).mulAdd(lateral, lateralSpeed);
        velocity.limit(MAX_SPEED);

        // 8. Integrate position.
        position.mulAdd(velocity, dt);
    }

    public float getSpeed() {
        return velocity.len();
    }

    /** Keeps an angle in the range (-PI, PI]. */
    static float wrapAngle(float angle) {
        while (angle > MathUtils.PI) {
            angle -= MathUtils.PI2;
        }
        while (angle <= -MathUtils.PI) {
            angle += MathUtils.PI2;
        }
        return angle;
    }
}
