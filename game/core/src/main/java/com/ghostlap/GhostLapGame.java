package com.ghostlap;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.ghostlap.net.ApiClient;
import com.ghostlap.net.LapUpload;
import com.ghostlap.net.Session;
import com.ghostlap.perf.Benchmark;
import com.ghostlap.perf.StartupTimer;
import com.ghostlap.race.RaceScreen;
import com.ghostlap.ui.LoginScreen;
import com.ghostlap.ui.ResultsScreen;
import com.ghostlap.ui.UiSkin;

/**
 * Entry point shared by all platforms. Owns what lives longer than one screen: the API client,
 * the UI skin and the login session. Screens: Login -> Race <-> Results/Leaderboard.
 */
public class GhostLapGame extends Game {

    private final String playerServiceUrl;
    private final String raceServiceUrl;

    private ApiClient api;
    private Skin skin;
    /** The logged-in player, or null when playing offline. Memory only: never saved to disk. */
    private Session session;
    private String lastUsername = "";
    /** A lap that couldn't be sent (server down or login expired); retried at the next race start. */
    private LapUpload unsentLap;
    /** Set in benchmark mode: skip login, the car drives itself, frames are measured (see Benchmark). */
    private final boolean benchmarkMode;
    private Benchmark benchmark;

    public GhostLapGame(String playerServiceUrl, String raceServiceUrl, boolean benchmarkMode) {
        this.playerServiceUrl = playerServiceUrl;
        this.raceServiceUrl = raceServiceUrl;
        this.benchmarkMode = benchmarkMode;
    }

    @Override
    public void create() {
        StartupTimer.createStarted();
        skin = UiSkin.create();
        // Network results are handed to the render thread with postRunnable (see ApiClient).
        api = new ApiClient(playerServiceUrl, raceServiceUrl, runnable -> Gdx.app.postRunnable(runnable));
        if (benchmarkMode) {
            benchmark = new Benchmark();
            switchTo(new RaceScreen(this));
        } else {
            switchTo(new LoginScreen(this, null));
        }
    }

    @Override
    public void render() {
        if (benchmark != null) {
            benchmark.frameStarted();
        }
        super.render(); // renders the current screen
        if (benchmark != null) {
            benchmark.frameEnded();
        }
        if (getScreen() != null) {
            StartupTimer.frameRendered();
        }
    }

    public boolean isBenchmarkMode() {
        return benchmarkMode;
    }

    // ---- navigation ----

    public void showLogin(String message) {
        switchTo(new LoginScreen(this, message));
    }

    public void showRace() {
        switchTo(new RaceScreen(this));
    }

    public void showResults(String lastLapMessage) {
        switchTo(new ResultsScreen(this, lastLapMessage));
    }

    /**
     * Swaps screens at the start of the next frame and disposes the old one. Doing it later (not in
     * the middle of the old screen's render) means the old screen never draws with freed resources.
     */
    private void switchTo(Screen next) {
        Gdx.app.postRunnable(() -> {
            Screen old = getScreen();
            setScreen(next);
            if (old != null) {
                old.dispose();
            }
        });
    }

    // ---- session ----

    public void startSession(Session newSession) {
        session = newSession;
        lastUsername = newSession.username();
        showRace();
    }

    /** Forget the token (logout or expired). The game keeps running offline. */
    public void endSession() {
        session = null;
    }

    public void playOffline() {
        session = null;
        showRace();
    }

    public Session getSession() {
        return session;
    }

    public String getLastUsername() {
        return lastUsername;
    }

    public void setUnsentLap(LapUpload lap) {
        unsentLap = lap;
    }

    /** Returns the unsent lap (or null) and forgets it. */
    public LapUpload takeUnsentLap() {
        LapUpload lap = unsentLap;
        unsentLap = null;
        return lap;
    }

    public ApiClient getApi() {
        return api;
    }

    public Skin getSkin() {
        return skin;
    }

    @Override
    public void dispose() {
        if (getScreen() != null) {
            getScreen().dispose();
        }
        skin.dispose();
        api.shutdown();
    }
}
