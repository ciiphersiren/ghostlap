package com.ghostlap.track;

import com.badlogic.gdx.math.Vector2;

/**
 * A line across the track. {@code inner}/{@code outer} are where it meets the track edges (used
 * for drawing). The detection line is a bit longer, so a car that is slightly off-track still counts.
 */
public class Checkpoint {
    public final int index;
    public final Vector2 inner;
    public final Vector2 outer;
    final Vector2 detectA;
    final Vector2 detectB;

    Checkpoint(int index, Vector2 inner, Vector2 outer, float detectMargin) {
        this.index = index;
        this.inner = inner;
        this.outer = outer;
        Vector2 across = new Vector2(outer).sub(inner).nor().scl(detectMargin);
        this.detectA = new Vector2(inner).sub(across);
        this.detectB = new Vector2(outer).add(across);
    }
}
