package com.ghostlap.shared.ghost;

/** One recorded moment of a lap: where the car was and which way it pointed (radians). */
public record GhostSample(float x, float y, float heading) {
}
