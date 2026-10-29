package com.ghostlap.car;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.ghostlap.GameConstants.LATERAL_GRIP;
import static com.ghostlap.GameConstants.MAX_SPEED;
import static com.ghostlap.GameConstants.PHYSICS_HZ;
import static com.ghostlap.GameConstants.PHYSICS_STEP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CarTest {

    private Car car;
    private CarInput input;

    @BeforeEach
    void setUp() {
        car = new Car();
        car.reset(new Vector2(0, 0), 0f); // facing +x
        input = new CarInput();
    }

    private void run(int ticks, boolean onTrack) {
        for (int i = 0; i < ticks; i++) {
            car.update(input, onTrack, PHYSICS_STEP);
        }
    }

    @Test
    void throttleMovesTheCarAlongItsHeading() {
        input.throttle = true;
        run(PHYSICS_HZ, true);

        assertThat(car.position.x).isGreaterThan(100f);
        assertThat(car.position.y).isCloseTo(0f, within(0.001f));
    }

    @Test
    void speedNeverExceedsMaxSpeed() {
        input.throttle = true;
        input.steer = 1f;
        for (int i = 0; i < 20 * PHYSICS_HZ; i++) {
            car.update(input, true, PHYSICS_STEP);
            assertThat(car.getSpeed()).isLessThanOrEqualTo(MAX_SPEED + 0.01f);
        }
    }

    @Test
    void offTrackIsMuchSlowerThanOnTrack() {
        input.throttle = true;
        run(5 * PHYSICS_HZ, true);
        float onTrackSpeed = car.getSpeed();

        car.reset(new Vector2(0, 0), 0f);
        run(5 * PHYSICS_HZ, false);
        float offTrackSpeed = car.getSpeed();

        assertThat(offTrackSpeed).isLessThan(onTrackSpeed * 0.5f);
    }

    @Test
    void gripRemovesPartOfSidewaysVelocityEachTick() {
        car.velocity.set(0f, 100f); // sliding purely sideways (car faces +x)
        run(1, true);

        assertThat(car.velocity.y).isCloseTo(100f * (1f - LATERAL_GRIP), within(0.01f));
    }

    @Test
    void parkedCarCannotSpinOnTheSpot() {
        input.steer = 1f;
        run(PHYSICS_HZ, true);

        assertThat(car.heading).isZero();
    }

    @Test
    void steeringLeftTurnsCounterClockwise() {
        input.throttle = true;
        input.steer = 1f;
        run(30, true);

        assertThat(car.heading).isPositive();
    }

    @Test
    void wrapAngleKeepsAnglesInRange() {
        assertThat(Car.wrapAngle(3 * MathUtils.PI)).isCloseTo(MathUtils.PI, within(0.0001f));
        assertThat(Car.wrapAngle(-1.5f * MathUtils.PI)).isCloseTo(0.5f * MathUtils.PI, within(0.0001f));
    }
}
