package com.ghostlap.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.ghostlap.track.Checkpoint;
import com.ghostlap.track.Track;

/** Draws the track with a ShapeRenderer that is already in Filled mode. */
public class TrackRenderer {

    private static final Color ASPHALT = new Color(0.25f, 0.25f, 0.28f, 1f);
    private static final Color EDGE = Color.WHITE;
    private static final Color CHECKPOINT = new Color(1f, 0.85f, 0.2f, 1f);
    private static final Color NEXT_CHECKPOINT = new Color(0.2f, 1f, 0.4f, 1f);
    private static final float EDGE_THICKNESS = 4f;
    private static final float CHECKPOINT_THICKNESS = 3f;
    private static final float START_LINE_THICKNESS = 8f;

    public void draw(ShapeRenderer shapes, Track track, int nextCheckpoint) {
        Vector2[] inner = track.getInnerEdge();
        Vector2[] outer = track.getOuterEdge();
        int n = inner.length;

        // Road surface: two triangles per quad between neighbouring samples.
        shapes.setColor(ASPHALT);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            shapes.triangle(inner[i].x, inner[i].y, outer[i].x, outer[i].y, outer[j].x, outer[j].y);
            shapes.triangle(inner[i].x, inner[i].y, outer[j].x, outer[j].y, inner[j].x, inner[j].y);
        }

        // Edges
        shapes.setColor(EDGE);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            shapes.rectLine(inner[i], inner[j], EDGE_THICKNESS);
            shapes.rectLine(outer[i], outer[j], EDGE_THICKNESS);
        }

        // Checkpoints: start/finish is thick, the one to hit next is green.
        for (Checkpoint cp : track.getCheckpoints()) {
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
}
