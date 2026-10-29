package com.ghostlap.ghost;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostSample;

import java.util.List;

/**
 * Plays a ghost back. Samples are 1/30 s apart, but we render far more often than that, so we
 * blend between the two samples either side of the current time.
 */
public class GhostPlayer {

    private final Ghost ghost;

    public GhostPlayer(Ghost ghost) {
        this.ghost = ghost;
    }

    public Ghost getGhost() {
        return ghost;
    }

    /**
     * Where the ghost was {@code seconds} after crossing the start line. Writes the position into
     * {@code outPosition} and returns the heading. Before the start or after the end, it holds still.
     */
    public float sample(float seconds, Vector2 outPosition) {
        List<GhostSample> samples = ghost.samples();
        if (samples.isEmpty()) {
            outPosition.setZero();
            return 0f;
        }

        // e.g. 2.51 s * 30 Hz = sample 75.3 -> 30% of the way from sample 75 to sample 76
        float exactIndex = seconds * ghost.sampleRateHz();
        int last = samples.size() - 1;
        if (exactIndex <= 0f) {
            return copy(samples.get(0), outPosition);
        }
        if (exactIndex >= last) {
            return copy(samples.get(last), outPosition);
        }
        int i = (int) exactIndex;
        float t = exactIndex - i;
        GhostSample a = samples.get(i);
        GhostSample b = samples.get(i + 1);

        // Linear interpolation: a + (b - a) * t
        outPosition.set(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
        return lerpAngle(a.heading(), b.heading(), t);
    }

    public boolean isFinished(float seconds) {
        return seconds * ghost.sampleRateHz() >= ghost.sampleCount() - 1;
    }

    /**
     * Interpolates between two angles the short way round. Going from 170 to -170 degrees is a
     * 20 degree turn through 180, not a 340 degree spin through 0.
     */
    static float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        // Headings are in (-PI, PI], so diff is in (-2PI, 2PI). One correction puts it in [-PI, PI].
        if (diff > MathUtils.PI) {
            diff -= MathUtils.PI2;
        } else if (diff < -MathUtils.PI) {
            diff += MathUtils.PI2;
        }
        return from + diff * t;
    }

    private static float copy(GhostSample sample, Vector2 outPosition) {
        outPosition.set(sample.x(), sample.y());
        return sample.heading();
    }
}
