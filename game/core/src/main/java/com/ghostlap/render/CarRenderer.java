package com.ghostlap.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import static com.ghostlap.shared.GameConstants.CAR_LENGTH;
import static com.ghostlap.shared.GameConstants.CAR_WIDTH;

/** Draws a car as a rotated rectangle with a dark windscreen so you can see which end is the front. */
public class CarRenderer {

    private static final Color WINDSCREEN = new Color(0.1f, 0.1f, 0.15f, 1f);

    /**
     * {@code alpha} is opacity: 1 = solid, lower = see-through (used for the ghost car).
     * Blending must be enabled for alpha below 1 to have any effect.
     */
    public void draw(ShapeRenderer shapes, float x, float y, float heading, Color bodyColor, float alpha) {
        float degrees = heading * MathUtils.radiansToDegrees;

        // rect(x, y, originX, originY, ...) rotates around the origin, which we put at the centre.
        shapes.setColor(bodyColor.r, bodyColor.g, bodyColor.b, alpha);
        shapes.rect(x - CAR_LENGTH / 2f, y - CAR_WIDTH / 2f, CAR_LENGTH / 2f, CAR_WIDTH / 2f,
                CAR_LENGTH, CAR_WIDTH, 1f, 1f, degrees);

        // Windscreen: a small rectangle a little ahead of the centre.
        float screenLength = CAR_LENGTH * 0.2f;
        float screenWidth = CAR_WIDTH * 0.8f;
        float offset = CAR_LENGTH * 0.15f;
        float sx = x + MathUtils.cos(heading) * offset;
        float sy = y + MathUtils.sin(heading) * offset;
        shapes.setColor(WINDSCREEN.r, WINDSCREEN.g, WINDSCREEN.b, alpha);
        shapes.rect(sx - screenLength / 2f, sy - screenWidth / 2f, screenLength / 2f, screenWidth / 2f,
                screenLength, screenWidth, 1f, 1f, degrees);
    }
}
