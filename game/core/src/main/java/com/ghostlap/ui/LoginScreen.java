package com.ghostlap.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.ghostlap.GhostLapGame;
import com.ghostlap.net.ApiCallback;
import com.ghostlap.net.ApiError;
import com.ghostlap.net.Session;

/**
 * Username + password, with Login, Register and Play offline. Built with scene2d.ui: a Stage holds
 * the widgets, a Table lays them out in rows and columns, and listeners react to clicks.
 */
public class LoginScreen extends ScreenAdapter {

    private static final Color BACKGROUND = new Color(0.08f, 0.09f, 0.11f, 1f);
    private static final float FIELD_WIDTH = 320f;

    private final GhostLapGame game;
    private final Stage stage = new Stage(new ScreenViewport());
    private final TextField usernameField;
    private final TextField passwordField;
    private final TextButton loginButton;
    private final TextButton registerButton;
    private final TextButton offlineButton;
    private final Label statusLabel;
    /** Set when this screen is gone, so a late network answer is ignored. */
    private boolean disposed;

    /** @param message shown under the buttons, e.g. "Your login expired", or null */
    public LoginScreen(GhostLapGame game, String message) {
        this.game = game;
        Skin skin = game.getSkin();

        usernameField = new TextField(game.getLastUsername(), skin);
        usernameField.setMessageText("username");
        passwordField = new TextField("", skin);
        passwordField.setMessageText("password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');
        // Enter in the password field logs in.
        passwordField.setTextFieldListener((field, c) -> {
            if (c == '\r' || c == '\n') {
                submit(false);
            }
        });

        loginButton = new TextButton("Log in", skin);
        registerButton = new TextButton("Register", skin);
        offlineButton = new TextButton("Play offline", skin);
        loginButton.addListener(onClick(() -> submit(false)));
        registerButton.addListener(onClick(() -> submit(true)));
        offlineButton.addListener(onClick(game::playOffline));

        statusLabel = new Label(message == null ? "" : message, skin);
        statusLabel.setWrap(true);
        statusLabel.setAlignment(Align.center);
        statusLabel.setColor(Color.ORANGE);

        Table buttons = new Table();
        buttons.defaults().pad(0, 6, 0, 6);
        buttons.add(loginButton);
        buttons.add(registerButton);
        buttons.add(offlineButton);

        Table table = new Table();
        table.setFillParent(true);
        table.defaults().pad(6);
        table.add(new Label("GhostLap", skin, "title")).colspan(2).padBottom(30).row();
        table.add(new Label("Username", skin)).right();
        table.add(usernameField).width(FIELD_WIDTH).row();
        table.add(new Label("Password", skin)).right();
        table.add(passwordField).width(FIELD_WIDTH).row();
        table.add(buttons).colspan(2).padTop(20).row();
        table.add(statusLabel).colspan(2).width(700).padTop(20).row();
        table.add(new Label("Server: " + game.getApi().getPlayerServiceUrl(), skin)).colspan(2).padTop(30);
        stage.addActor(table);
    }

    private void submit(boolean register) {
        if (loginButton.isDisabled()) {
            return; // a request is already in flight (Enter pressed twice)
        }
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Enter a username and a password.");
            return;
        }
        setBusy(true);
        showStatus(register ? "Creating account..." : "Logging in...");

        ApiCallback<Session> callback = new ApiCallback<>() {
            @Override
            public void onSuccess(Session session) {
                if (!disposed) {
                    game.startSession(session);
                }
            }

            @Override
            public void onFailure(ApiError error) {
                if (disposed) {
                    return;
                }
                setBusy(false);
                showStatus(describe(error));
            }
        };
        // Returns immediately; the answer arrives a few frames later through the callback.
        if (register) {
            game.getApi().register(username, password, callback);
        } else {
            game.getApi().login(username, password, callback);
        }
    }

    private String describe(ApiError error) {
        if (error.kind() == ApiError.Kind.UNREACHABLE) {
            return "Can't reach the server at " + game.getApi().getPlayerServiceUrl()
                    + ". Is docker compose running? You can still play offline.";
        }
        return error.message(); // e.g. "Invalid username or password", "Username 'x' is already taken"
    }

    private void showStatus(String text) {
        statusLabel.setText(text);
    }

    /** Disables the buttons while a request is in flight, so it can't be sent twice. (Disabled buttons don't fire.) */
    private void setBusy(boolean busy) {
        loginButton.setDisabled(busy);
        registerButton.setDisabled(busy);
        offlineButton.setDisabled(busy);
    }

    private static ChangeListener onClick(Runnable action) {
        return new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        };
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        stage.setKeyboardFocus(usernameField.getText().isEmpty() ? usernameField : passwordField);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(BACKGROUND);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        disposed = true;
        stage.dispose();
    }
}
