package com.ghostlap.track;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ghostlap.shared.GameConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrackTest {

    private final Track track = Track.createDefault();

    @Test
    void hasEightCheckpointsAndStartsOnTrack() {
        assertThat(track.getCheckpoints()).hasSize(GameConstants.CHECKPOINT_COUNT);
        assertThat(track.isOnTrack(track.getStartPosition())).isTrue();
        assertThat(track.isOnTrack(new Vector2(5000, 5000))).isFalse();
    }

    @Test
    void checkpointsAreEvenlySpacedAlongTheTrack() {
        // Distance between consecutive checkpoint midpoints should be similar (straight-line
        // distance is always <= the distance along the curve, so we only check a loose range).
        float expected = track.getLength() / GameConstants.CHECKPOINT_COUNT;
        Checkpoint[] cps = track.getCheckpoints();
        for (int i = 0; i < cps.length; i++) {
            Vector2 a = new Vector2(cps[i].inner).add(cps[i].outer).scl(0.5f);
            Checkpoint next = cps[(i + 1) % cps.length];
            Vector2 b = new Vector2(next.inner).add(next.outer).scl(0.5f);
            assertThat(a.dst(b)).isBetween(expected * 0.6f, expected * 1.01f);
        }
    }

    @Test
    void drivingForwardFromTheStartCrossesTheStartLine() {
        Vector2 from = track.getStartPosition();
        float heading = track.getStartHeading();
        Vector2 to = new Vector2(MathUtils.cos(heading), MathUtils.sin(heading)).scl(200f).add(from);

        assertThat(track.findCrossedCheckpoint(from, to)).isZero();
    }

    @Test
    void edgesAreHalfAWidthFromTheCenterline() {
        Vector2[] center = track.getCenterline();
        for (int i = 0; i < center.length; i++) {
            assertThat(center[i].dst(track.getInnerEdge()[i])).isCloseTo(track.getWidth() / 2f, within(0.01f));
            assertThat(center[i].dst(track.getOuterEdge()[i])).isCloseTo(track.getWidth() / 2f, within(0.01f));
        }
    }

    /**
     * Parts of the track that are far apart along the road must also be far apart in space,
     * otherwise two sections of road would overlap.
     */
    @Test
    void trackDoesNotOverlapItself() {
        Vector2[] center = track.getCenterline();
        int n = center.length;
        float[] distance = new float[n];
        for (int i = 1; i < n; i++) {
            distance[i] = distance[i - 1] + center[i].dst(center[i - 1]);
        }
        float minAlongRoad = MathUtils.PI * track.getWidth();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                float along = distance[j] - distance[i];
                along = Math.min(along, track.getLength() - along); // the loop wraps around
                if (along > minAlongRoad) {
                    assertThat(center[i].dst(center[j]))
                            .as("samples %d and %d", i, j)
                            .isGreaterThan(track.getWidth() * 1.2f);
                }
            }
        }
    }

    /** If a curve is tighter than half the track width, the inner edge folds over itself. */
    @Test
    void curvesAreNotTighterThanHalfTheTrackWidth() {
        Vector2[] center = track.getCenterline();
        int n = center.length;
        for (int i = 0; i < n; i++) {
            Vector2 a = center[(i - 1 + n) % n];
            Vector2 b = center[i];
            Vector2 c = center[(i + 1) % n];
            assertThat(circumradius(a, b, c)).as("sample %d", i).isGreaterThan(track.getWidth() / 2f);
        }
    }

    /** Radius of the circle through three points: R = abc / (4 * area). */
    private static float circumradius(Vector2 a, Vector2 b, Vector2 c) {
        float ab = a.dst(b);
        float bc = b.dst(c);
        float ca = c.dst(a);
        float area = Math.abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) / 2f;
        if (area < 1e-4f) {
            return Float.MAX_VALUE; // straight line
        }
        return ab * bc * ca / (4f * area);
    }
}
