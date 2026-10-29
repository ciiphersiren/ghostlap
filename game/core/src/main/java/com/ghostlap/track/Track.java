package com.ghostlap.track;

import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ghostlap.GameConstants;

/**
 * A closed-loop track. A Catmull-Rom spline passes smoothly through the control points; we sample
 * it into a polyline (the centerline) and push each sample sideways along the spline normal to get
 * the inner and outer edges. Checkpoints are placed at equal distances along the centerline.
 */
public class Track {

    /** How many points we sample along the spline. More = smoother edges, more work. */
    static final int SAMPLES = 300;
    /** Sample index (counted back from the start line) where the car spawns. */
    private static final int START_OFFSET_SAMPLES = 3;

    private final float width;
    private final Vector2[] center = new Vector2[SAMPLES];
    private final Vector2[] inner = new Vector2[SAMPLES];
    private final Vector2[] outer = new Vector2[SAMPLES];
    private final Checkpoint[] checkpoints;
    private final float length;
    private final Vector2 startPosition = new Vector2();
    private final float startHeading;

    // Scratch vector for intersection tests, so per-tick checks don't allocate.
    private final Vector2 hit = new Vector2();

    public Track(Vector2[] controlPoints, float width, int checkpointCount) {
        this.width = width;
        float halfWidth = width / 2f;

        // continuous = true makes the spline loop back to the first point.
        CatmullRomSpline<Vector2> spline = new CatmullRomSpline<>(controlPoints, true);

        Vector2 tangent = new Vector2();
        for (int i = 0; i < SAMPLES; i++) {
            float t = (float) i / SAMPLES; // t goes from 0 to just under 1 around the loop
            center[i] = spline.valueAt(new Vector2(), t);
            spline.derivativeAt(tangent, t);
            // Rotating the tangent 90 degrees gives the normal (points to the left of travel).
            Vector2 normal = new Vector2(-tangent.y, tangent.x).nor();
            inner[i] = new Vector2(center[i]).mulAdd(normal, halfWidth);
            outer[i] = new Vector2(center[i]).mulAdd(normal, -halfWidth);
        }

        // Cumulative distance along the centerline. Spline t is not proportional to distance,
        // so we measure it to space checkpoints evenly.
        float[] distance = new float[SAMPLES + 1];
        for (int i = 1; i <= SAMPLES; i++) {
            distance[i] = distance[i - 1] + center[i % SAMPLES].dst(center[i - 1]);
        }
        length = distance[SAMPLES];

        checkpoints = new Checkpoint[checkpointCount];
        int sample = 0;
        for (int k = 0; k < checkpointCount; k++) {
            float target = k * length / checkpointCount;
            while (distance[sample] < target) {
                sample++;
            }
            checkpoints[k] = new Checkpoint(k, inner[sample], outer[sample], halfWidth);
        }

        // Spawn just behind checkpoint 0 (the start/finish line), facing along the track.
        int startSample = SAMPLES - START_OFFSET_SAMPLES;
        Vector2 next = center[(startSample + 1) % SAMPLES];
        startPosition.set(center[startSample]);
        startHeading = MathUtils.atan2(next.y - startPosition.y, next.x - startPosition.x);
    }

    /** The single track used in GhostLap: 12 control points, driven counter-clockwise. */
    public static Track createDefault() {
        Vector2[] points = {
                new Vector2(0, 0),
                new Vector2(900, -100),
                new Vector2(1700, 0),
                new Vector2(2300, 300),
                new Vector2(2500, 900),
                new Vector2(2100, 1300),
                new Vector2(1500, 1100),
                new Vector2(1100, 1500),
                new Vector2(1300, 2000),
                new Vector2(700, 2300),
                new Vector2(0, 2000),
                new Vector2(-400, 1100),
        };
        return new Track(points, GameConstants.TRACK_WIDTH, GameConstants.CHECKPOINT_COUNT);
    }

    /** Shortest distance from {@code point} to the centerline polyline. */
    public float distanceToCenterline(Vector2 point) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            Vector2 a = center[i];
            Vector2 b = center[(i + 1) % SAMPLES];
            float d = Intersector.distanceSegmentPoint(a, b, point);
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    public boolean isOnTrack(Vector2 point) {
        return distanceToCenterline(point) <= width / 2f;
    }

    /**
     * Returns the index of the checkpoint crossed by moving from {@code from} to {@code to}
     * during one tick, or -1 if none was crossed.
     */
    public int findCrossedCheckpoint(Vector2 from, Vector2 to) {
        for (Checkpoint cp : checkpoints) {
            if (Intersector.intersectSegments(from, to, cp.detectA, cp.detectB, hit)) {
                return cp.index;
            }
        }
        return -1;
    }

    public Vector2[] getCenterline() {
        return center;
    }

    public Vector2[] getInnerEdge() {
        return inner;
    }

    public Vector2[] getOuterEdge() {
        return outer;
    }

    public Checkpoint[] getCheckpoints() {
        return checkpoints;
    }

    public float getWidth() {
        return width;
    }

    public float getLength() {
        return length;
    }

    public Vector2 getStartPosition() {
        return startPosition;
    }

    public float getStartHeading() {
        return startHeading;
    }
}
