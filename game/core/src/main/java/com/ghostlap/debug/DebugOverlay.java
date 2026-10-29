package com.ghostlap.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Performance and state readout, toggled with F3. Draws in screen coordinates. */
public class DebugOverlay {

    private static final float LINE_HEIGHT = 22f;
    private static final long MB = 1024L * 1024L;

    private boolean visible;

    public void toggle() {
        visible = !visible;
    }

    public void draw(SpriteBatch batch, BitmapFont font, float x, float topY,
                     float speed, int nextCheckpoint, int checkpointCount, boolean onTrack) {
        if (!visible) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        long usedHeap = (runtime.totalMemory() - runtime.freeMemory()) / MB;
        long maxHeap = runtime.maxMemory() / MB;
        float frameMs = Gdx.graphics.getDeltaTime() * 1000f;

        String[] lines = {
                "FPS: " + Gdx.graphics.getFramesPerSecond(),
                String.format("Frame: %.2f ms", frameMs),
                "Heap: " + usedHeap + " / " + maxHeap + " MB",
                String.format("Speed: %.0f u/s", speed),
                "Next checkpoint: " + nextCheckpoint + " / " + checkpointCount,
                "On track: " + (onTrack ? "yes" : "no"),
        };

        font.setColor(Color.YELLOW);
        float y = topY;
        for (String line : lines) {
            font.draw(batch, line, x, y);
            y -= LINE_HEIGHT;
        }
        font.setColor(Color.WHITE);
    }
}
