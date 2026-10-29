package com.ghostlap.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.ghostlap.track.Checkpoint;
import com.ghostlap.track.Track;

/**
 * Draws the track with a ShapeRenderer that is already in Filled mode.
 *
 * Culling: the camera only shows part of the track, so segments completely outside the view are
 * skipped instead of being sent to the GPU every frame. A cheap bounding-box test per segment
 * saves most of the vertices (measured in docs/PERFORMANCE.md).
 */
public class TrackRenderer {

    private static final Color ASPHALT = new Color(0.25f, 0.25f, 0.28f, 1f);
    private static final Color EDGE = Color.WHITE;
    private static final Color CHECKPOINT = new Color(1f, 0.85f, 0.2f, 1f);
    private static final Color NEXT_CHECKPOINT = new Color(0.2f, 1f, 0.4f, 1f);
    private static final float EDGE_THICKNESS = 4f;
    private static final float CHECKPOINT_THICKNESS = 3f;
    private static final float START_LINE_THICKNESS = 8f;
    /** Extra border around the view, so thick lines at the edge of the screen are never cut off. */
    private static final float VIEW_MARGIN = 20f;

    // Reused every frame, so culling doesn't allocate.
    private final Rectangle view = new Rectangle();
    private boolean[] segmentVisible = new boolean[0];

    public void draw(ShapeRenderer shapes, Track track, int nextCheckpoint, OrthographicCamera camera) {
        Vector2[] inner = track.getInnerEdge();
        Vector2[] outer = track.getOuterEdge();
        int n = inner.length;
        updateView(camera);

        // Which segments (the quad between sample i and i+1) touch the view?
        if (segmentVisible.length != n) {
            segmentVisible = new boolean[n];
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            segmentVisible[i] = overlapsView(inner[i], outer[i], inner[j], outer[j]);
        }

        // Road surface: two triangles per quad between neighbouring samples.
        shapes.setColor(ASPHALT);
        for (int i = 0; i < n; i++) {
            if (!segmentVisible[i]) {
                continue;
            }
            int j = (i + 1) % n;
            shapes.triangle(inner[i].x, inner[i].y, outer[i].x, outer[i].y, outer[j].x, outer[j].y);
            shapes.triangle(inner[i].x, inner[i].y, outer[j].x, outer[j].y, inner[j].x, inner[j].y);
        }

        // Edges
        shapes.setColor(EDGE);
        for (int i = 0; i < n; i++) {
            if (!segmentVisible[i]) {
                continue;
            }
            int j = (i + 1) % n;
            shapes.rectLine(inner[i], inner[j], EDGE_THICKNESS);
            shapes.rectLine(outer[i], outer[j], EDGE_THICKNESS);
        }

        // Checkpoints: start/finish is thick, the one to hit next is green.
        for (Checkpoint cp : track.getCheckpoints()) {
            if (!overlapsView(cp.inner, cp.outer, cp.inner, cp.outer)) {
                continue;
            }
            if (cp.index == nextCheckpoint) {
                shapes.setColor(NEXT_CHECKPOINT);
            } else if (cp.index == 0) {
                shapes.setColor(EDGE);
            } else {
                shapes.setColor(CHECKPOINT);
            }
            float thickness = cp.index == 0 ? START_LINE_THICKNESS : CHECKPOINT_THICKNESS;
            shapes.rectLine(cp.inner, cp.outer, thickness);
        }
    }

    /** The part of the world the camera shows, plus a margin. */
    private void updateView(OrthographicCamera camera) {
        float halfWidth = camera.viewportWidth * camera.zoom / 2f + VIEW_MARGIN;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f + VIEW_MARGIN;
        view.set(camera.position.x - halfWidth, camera.position.y - halfHeight, 2 * halfWidth, 2 * halfHeight);
    }

    /** Bounding-box test: does the box around these four points overlap the view? */
    private boolean overlapsView(Vector2 a, Vector2 b, Vector2 c, Vector2 d) {
        float minX = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x));
        float maxX = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x));
        float minY = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
        float maxY = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));
        return maxX >= view.x && minX <= view.x + view.width
                && maxY >= view.y && minY <= view.y + view.height;
    }
}
