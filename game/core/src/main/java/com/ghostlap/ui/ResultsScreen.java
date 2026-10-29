package com.ghostlap.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.ghostlap.GhostLapGame;
import com.ghostlap.net.ApiCallback;
import com.ghostlap.net.ApiError;
import com.ghostlap.net.LeaderboardRow;
import com.ghostlap.net.Session;
import com.ghostlap.race.LapTimer;
import com.ghostlap.shared.GameConstants;

import java.util.List;

/** The last lap's verdict plus the track's top 10 from race-service. Opened with Tab during a race. */
public class ResultsScreen extends ScreenAdapter {

    private static final Color BACKGROUND = new Color(0.08f, 0.09f, 0.11f, 1f);
    private static final int TOP = 10;

    private final GhostLapGame game;
    private final Skin skin;
    private final Stage stage = new Stage(new ScreenViewport());
    /** Filled in when the leaderboard arrives. */
    private final Table rows = new Table();
    private boolean disposed;

    /** @param lastLapMessage e.g. "Lap 00:15.333 ACCEPTED - rank #2", or null */
    public ResultsScreen(GhostLapGame game, String lastLapMessage) {
        this.game = game;
        this.skin = game.getSkin();
        Session session = game.getSession();

        TextButton raceButton = new TextButton("Race (Enter)", skin);
        raceButton.addListener(onClick(game::showRace));
        TextButton accountButton = new TextButton(session == null ? "Log in" : "Log out", skin);
        accountButton.addListener(onClick(() -> {
            game.endSession();
            game.showLogin(null);
        }));

        Table buttons = new Table();
        buttons.defaults().pad(0, 6, 0, 6);
        buttons.add(raceButton);
        buttons.add(accountButton);

        Table table = new Table();
        table.setFillParent(true);
        table.defaults().pad(6);
        table.add(new Label("Leaderboard - top " + TOP, skin, "title")).padBottom(10).row();
        String who = session == null ? "Playing offline" : "Logged in as " + session.username();
        table.add(new Label(who, skin)).row();
        if (lastLapMessage != null) {
            table.add(new Label(lastLapMessage, skin)).padBottom(10).row();
        }
        table.add(rows).padTop(10).padBottom(20).row();
        table.add(buttons);
        stage.addActor(table);

        // Enter or Escape goes back to racing.
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Keys.ENTER || keycode == Keys.ESCAPE) {
                    game.showRace();
                    return true;
                }
                return false;
            }
        });

        rows.add(new Label("Loading leaderboard...", skin));
        loadLeaderboard();
    }

    /** The leaderboard is public, so it loads even when playing offline (if the server is up). */
    private void loadLeaderboard() {
        game.getApi().fetchLeaderboard(GameConstants.TRACK_ID, TOP, new ApiCallback<>() {
            @Override
            public void onSuccess(List<LeaderboardRow> result) {
                if (!disposed) {
                    showRows(result);
                }
            }

            @Override
            public void onFailure(ApiError error) {
                if (disposed) {
                    return;
                }
                rows.clear();
                String text = error.kind() == ApiError.Kind.UNREACHABLE
                        ? "Server unreachable - can't load the leaderboard right now."
                        : "Couldn't load the leaderboard: " + error.message();
                Label label = new Label(text, skin);
                label.setColor(Color.ORANGE);
                rows.add(label);
            }
        });
    }

    private void showRows(List<LeaderboardRow> result) {
        rows.clear();
        if (result.isEmpty()) {
            rows.add(new Label("No laps on the server yet. Be the first!", skin));
            return;
        }
        rows.defaults().pad(4, 20, 4, 20);
        rows.add(header("#")).right();
        rows.add(header("Player")).left();
        rows.add(header("Time")).right().row();
        Session session = game.getSession();
        for (LeaderboardRow row : result) {
            boolean me = session != null && row.username().equalsIgnoreCase(session.username());
            rows.add(cell(String.valueOf(row.rank()), me)).right();
            rows.add(cell(row.username(), me)).left();
            rows.add(cell(LapTimer.formatTime(row.lapTimeMs()), me)).right().row();
        }
    }

    private Label header(String text) {
        Label label = new Label(text, skin);
        label.setColor(Color.LIGHT_GRAY);
        return label;
    }

    /** Your own row is highlighted. */
    private Label cell(String text, boolean me) {
        Label label = new Label(text, skin);
        label.setColor(me ? Color.GOLD : Color.WHITE);
        return label;
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
