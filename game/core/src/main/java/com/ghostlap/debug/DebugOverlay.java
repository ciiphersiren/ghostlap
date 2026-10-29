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
    private final StringBuilder text = new StringBuilder(64);

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
        int frameMicros = (int) (Gdx.graphics.getDeltaTime() * 1_000_000f);

        // One reused StringBuilder per line: no new strings every frame (see RaceScreen.drawHud).
        font.setColor(Color.YELLOW);
        float y = topY;
        text.setLength(0);
        y = line(batch, font, x, y, text.append("FPS: ").append(Gdx.graphics.getFramesPerSecond()));
        text.setLength(0);
        text.append("Frame: ").append(frameMicros / 1000).append('.');
        int hundredths = (frameMicros % 1000) / 10;
        if (hundredths < 10) {
            text.append('0');
        }
        y = line(batch, font, x, y, text.append(hundredths).append(" ms"));
        text.setLength(0);
        y = line(batch, font, x, y, text.append("Heap: ").append(usedHeap).append(" / ").append(maxHeap).append(" MB"));
        text.setLength(0);
        y = line(batch, font, x, y, text.append("Speed: ").append(Math.round(speed)).append(" u/s"));
        text.setLength(0);
        y = line(batch, font, x, y, text.append("Next checkpoint: ").append(nextCheckpoint).append(" / ").append(checkpointCount));
        text.setLength(0);
        line(batch, font, x, y, text.append("On track: ").append(onTrack ? "yes" : "no"));
        font.setColor(Color.WHITE);
    }

    /** Draws one line and returns the y of the next one. */
    private static float line(SpriteBatch batch, BitmapFont font, float x, float y, CharSequence line) {
        font.draw(batch, line, x, y);
        return y - LINE_HEIGHT;
    }
}
