package com.ghostlap.car;

/** What the player is pressing this frame. Kept separate from the keyboard so physics is easy to test. */
public class CarInput {
    public boolean throttle;
    public boolean brake;
    /** -1 = full right, 0 = straight, +1 = full left. */
    public float steer;

    public void clear() {
        throttle = false;
        brake = false;
        steer = 0f;
    }
}
