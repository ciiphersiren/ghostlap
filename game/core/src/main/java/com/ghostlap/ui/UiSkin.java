package com.ghostlap.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/**
 * The look of the scene2d.ui widgets, built in code from a single white pixel (tinted per use) and
 * libGDX's built-in font. This avoids shipping skin files (json + atlas + png) for a few screens.
 */
public final class UiSkin {

    private static final Color FIELD = new Color(0.15f, 0.17f, 0.2f, 1f);
    private static final Color FIELD_FOCUSED = new Color(0.22f, 0.25f, 0.3f, 1f);
    private static final Color BUTTON = new Color(0.2f, 0.45f, 0.8f, 1f);
    private static final Color BUTTON_OVER = new Color(0.3f, 0.55f, 0.9f, 1f);
    private static final Color BUTTON_DOWN = new Color(0.15f, 0.35f, 0.65f, 1f);
    private static final Color BUTTON_DISABLED = new Color(0.3f, 0.3f, 0.33f, 1f);
    private static final float PADDING = 10f;

    private UiSkin() {
    }

    public static Skin create() {
        Skin skin = new Skin();

        // A 1x1 white texture; every background below is this pixel stretched and tinted.
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.setColor(Color.WHITE);
        pixel.fill();
        skin.add("white", new Texture(pixel));
        pixel.dispose();

        BitmapFont font = new BitmapFont();
        font.getData().setScale(1.4f);
        skin.add("default", font);

        skin.add("default", new Label.LabelStyle(font, Color.WHITE));
        skin.add("title", new Label.LabelStyle(font, Color.GOLD));

        TextField.TextFieldStyle field = new TextField.TextFieldStyle();
        field.font = font;
        field.fontColor = Color.WHITE;
        field.background = padded(skin.newDrawable("white", FIELD));
        field.focusedBackground = padded(skin.newDrawable("white", FIELD_FOCUSED));
        field.cursor = skin.newDrawable("white", Color.WHITE);
        field.cursor.setMinWidth(2f);
        field.selection = skin.newDrawable("white", BUTTON);
        skin.add("default", field);

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
        button.font = font;
        button.up = padded(skin.newDrawable("white", BUTTON));
        button.over = padded(skin.newDrawable("white", BUTTON_OVER));
        button.down = padded(skin.newDrawable("white", BUTTON_DOWN));
        button.disabled = padded(skin.newDrawable("white", BUTTON_DISABLED));
        button.disabledFontColor = Color.LIGHT_GRAY;
        skin.add("default", button);

        return skin;
    }

    /** Adds inner padding so text doesn't touch the edges of fields and buttons. */
    private static Drawable padded(Drawable drawable) {
        BaseDrawable d = (BaseDrawable) drawable;
        d.setLeftWidth(PADDING);
        d.setRightWidth(PADDING);
        d.setTopHeight(PADDING / 2);
        d.setBottomHeight(PADDING / 2);
        return d;
    }
}
