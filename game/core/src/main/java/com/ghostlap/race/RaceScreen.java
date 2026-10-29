package com.ghostlap.race;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.ghostlap.GhostLapGame;
import com.ghostlap.car.Car;
import com.ghostlap.car.CarInput;
import com.ghostlap.debug.DebugOverlay;
import com.ghostlap.ghost.GhostPlayer;
import com.ghostlap.ghost.GhostRecorder;
import com.ghostlap.ghost.GhostStorage;
import com.ghostlap.net.ApiCallback;
import com.ghostlap.net.ApiError;
import com.ghostlap.net.LapResult;
import com.ghostlap.net.LapUpload;
import com.ghostlap.net.RemoteGhost;
import com.ghostlap.net.Session;
import com.ghostlap.render.CarRenderer;
import com.ghostlap.render.TrackRenderer;
import com.ghostlap.shared.GameConstants;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostCodec;
import com.ghostlap.shared.ghost.GhostFormatException;
import com.ghostlap.track.Track;

import java.io.IOException;

/**
 * The race. Physics runs at a fixed 60 Hz using an accumulator; rendering runs as fast as the
 * display allows and interpolates the car between the last two physics states.
 *
 * Ghost: we race the fastest ghost we have, either the server's track record (downloaded at the
 * start) or the player's local best. Every completed lap is saved locally if it's a personal best
 * and, when logged in, uploaded to race-service in the background.
 */
public class RaceScreen extends ScreenAdapter {

    private static final float VIEW_WIDTH = 1600f;     // world units visible horizontally (at least)
    private static final float VIEW_HEIGHT = 900f;
    /** Cap on one frame's time, so a long pause (e.g. dragging the window) doesn't run hundreds of ticks. */
    private static final float MAX_FRAME_TIME = 0.25f;
    /** How quickly the camera catches up with the car. Higher = snappier. */
    private static final float CAMERA_FOLLOW_RATE = 5f;
    /** The camera aims this many seconds ahead of the car, so you see more of the road in front. */
    private static final float CAMERA_LOOK_AHEAD = 0.35f;
    private static final float HUD_MARGIN = 20f;

    private static final Color GRASS = new Color(0.18f, 0.45f, 0.2f, 1f);
    private static final Color PLAYER_COLOR = new Color(0.9f, 0.2f, 0.2f, 1f);
    private static final Color GHOST_COLOR = new Color(0.6f, 0.85f, 1f, 1f);
    private static final Color SERVER_COLOR = new Color(1f, 0.9f, 0.6f, 1f);
    private static final float GHOST_ALPHA = 0.45f;
    private static final String LOG_TAG = "GhostLap";

    private final GhostLapGame game;
    private final Track track = Track.createDefault();
    private final Car car = new Car();
    private final CarInput input = new CarInput();
    private final LapTimer lapTimer = new LapTimer(GameConstants.CHECKPOINT_COUNT);
    private final GhostRecorder recorder = new GhostRecorder();
    /** Local personal best on disk: per account when logged in, shared when offline. */
    private final GhostStorage ghostStorage;
    private int localBestMs = LapTimer.NO_TIME;

    /** The ghost we race against, or null if we have none yet. */
    private GhostPlayer ghostPlayer;
    private String ghostOwner = "";
    /** A faster ghost that arrived mid-lap; swapped in when the next lap starts so it doesn't jump. */
    private GhostPlayer pendingGhost;
    private String pendingGhostOwner;

    /** One line about the server (download/upload results, errors) shown on the HUD. */
    private String serverStatus = "";
    /** The last lap's verdict from the server, shown on the results screen. */
    private String lastLapMessage;
    /** Set when this screen is gone, so a late network answer doesn't touch it. */
    private boolean disposed;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ExtendViewport worldViewport = new ExtendViewport(VIEW_WIDTH, VIEW_HEIGHT, camera);
    private final ScreenViewport hudViewport = new ScreenViewport();

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont(); // libGDX's built-in default font
    private final TrackRenderer trackRenderer = new TrackRenderer();
    private final CarRenderer carRenderer = new CarRenderer();
    private final DebugOverlay debugOverlay = new DebugOverlay();

    private float accumulator;
    private boolean onTrack = true;
    private final Vector2 renderPosition = new Vector2();
    private final Vector2 cameraTarget = new Vector2();
    private final Vector2 ghostPosition = new Vector2();

    public RaceScreen(GhostLapGame game) {
        this.game = game;
        Session session = game.getSession();
        ghostStorage = session == null ? GhostStorage.inUserHome() : GhostStorage.forPlayer(session.username());
        font.getData().setScale(1.4f);
        loadLocalGhost();
        resetRace();
        connectToServer();
    }

    // ---- ghosts ----

    private void loadLocalGhost() {
        try {
            ghostStorage.load(GameConstants.TRACK_ID).ifPresent(ghost -> {
                localBestMs = ghost.lapTimeMs();
                setGhost(new GhostPlayer(ghost), "your best (local)");
                Gdx.app.log(LOG_TAG, "Loaded local ghost: " + LapTimer.formatTime(ghost.lapTimeMs())
                        + ", " + ghost.sampleCount() + " samples");
            });
        } catch (IOException | GhostFormatException e) {
            // A broken file shouldn't stop the game; the next best lap overwrites it.
            Gdx.app.error(LOG_TAG, "Could not load ghost from " + ghostStorage.fileFor(GameConstants.TRACK_ID), e);
        }
    }

    private void setGhost(GhostPlayer player, String owner) {
        ghostPlayer = player;
        ghostOwner = owner;
    }

    /** Uses the ghost if it's at least as fast as the current one; waits for the next lap if one is running. */
    private void offerGhost(GhostPlayer player, String owner) {
        if (ghostPlayer != null && ghostPlayer.getGhost().lapTimeMs() < player.getGhost().lapTimeMs()) {
            return;
        }
        if (lapTimer.isLapRunning()) {
            pendingGhost = player;
            pendingGhostOwner = owner;
        } else {
            setGhost(player, owner);
        }
    }

    private void applyPendingGhost() {
        if (pendingGhost != null) {
            GhostPlayer player = pendingGhost;
            pendingGhost = null;
            offerGhost(player, pendingGhostOwner);
        }
    }

    // ---- server ----

    /** Starts the background downloads/uploads. Nothing here waits: answers arrive in callbacks. */
    private void connectToServer() {
        Session session = game.getSession();
        if (session == null) {
            serverStatus = "Offline: laps are only saved on this computer";
            return;
        }
        serverStatus = "Logged in as " + session.username() + " - downloading the top ghost...";
        game.getApi().fetchTopGhost(GameConstants.TRACK_ID, new ApiCallback<>() {
            @Override
            public void onSuccess(RemoteGhost remote) {
                if (disposed) {
                    return;
                }
                boolean mine = remote.username().equalsIgnoreCase(session.username());
                offerGhost(new GhostPlayer(remote.ghost()), (mine ? "you" : remote.username()) + " (server #1)");
                serverStatus = "Track record: " + LapTimer.formatTime(remote.ghost().lapTimeMs()) + " by " + remote.username();
            }

            @Override
            public void onFailure(ApiError error) {
                if (disposed) {
                    return;
                }
                serverStatus = switch (error.kind()) {
                    case NOT_FOUND -> "No laps on the server yet - set the first one!";
                    case UNREACHABLE -> ghostPlayer != null
                            ? "Server unreachable - racing your local ghost"
                            : "Server unreachable - no ghost yet, set a lap!";
                    default -> "Couldn't download the top ghost: " + error.message();
                };
            }
        });

        LapUpload unsent = game.takeUnsentLap();
        if (unsent != null) {
            upload(unsent);
        }
    }

    private void upload(LapUpload lap) {
        Session session = game.getSession();
        if (session == null) {
            return;
        }
        String time = LapTimer.formatTime(lap.lapTimeMs());
        // The token's expiry is readable on the client, so don't even try with an expired one.
        if (session.isExpired()) {
            onLoginExpired(lap);
            return;
        }
        serverStatus = "Uploading lap " + time + "...";
        game.getApi().submitLap(session, lap, new ApiCallback<>() {
            @Override
            public void onSuccess(LapResult result) {
                String message = result.accepted()
                        ? "Lap " + time + " ACCEPTED" + (result.rank() != null ? " - rank #" + result.rank() : "")
                        : "Lap " + time + " REJECTED (" + result.reason() + ")";
                Gdx.app.log(LOG_TAG, message);
                if (!disposed) {
                    lastLapMessage = message;
                    serverStatus = message;
                }
            }

            @Override
            public void onFailure(ApiError error) {
                Gdx.app.error(LOG_TAG, "Lap upload failed: " + error);
                switch (error.kind()) {
                    case UNAUTHORIZED -> onLoginExpired(lap);
                    case UNREACHABLE -> {
                        game.setUnsentLap(lap); // keep it, try again at the next race start
                        if (!disposed) {
                            serverStatus = "Server unreachable - lap " + time + " will be sent next race";
                        }
                    }
                    default -> {
                        if (!disposed) {
                            serverStatus = "Upload failed: " + error.message();
                        }
                    }
                }
            }
        });
    }

    /** The token is no longer valid: log out, keep the lap, and tell the player how to continue. */
    private void onLoginExpired(LapUpload lap) {
        game.endSession();
        game.setUnsentLap(lap);
        if (!disposed) {
            serverStatus = "Login expired - press L to log in again (your lap will be sent)";
        }
    }

    // ---- race ----

    private void resetRace() {
        car.reset(track.getStartPosition(), track.getStartHeading());
        lapTimer.reset();
        recorder.stop();
        applyPendingGhost();
        accumulator = 0f;
        onTrack = true;
        camera.position.set(car.position, 0f);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(null); // the race polls the keyboard directly
    }

    @Override
    public void render(float delta) {
        handleKeys();
        readCarInput();

        // Fixed timestep: bank the real elapsed time, then spend it in exact 1/60 s steps.
        accumulator += Math.min(delta, MAX_FRAME_TIME);
        while (accumulator >= GameConstants.PHYSICS_STEP) {
            physicsStep();
            accumulator -= GameConstants.PHYSICS_STEP;
        }

        // alpha = how far we are between the previous tick and the current one (0..1).
        float alpha = accumulator / GameConstants.PHYSICS_STEP;
        renderPosition.set(car.previousPosition).lerp(car.position, alpha);
        float renderHeading = MathUtils.lerpAngle(car.previousHeading, car.heading, alpha);
        float ghostHeading = ghostPlayer != null ? ghostPlayer.sample(ghostTimeSeconds(alpha), ghostPosition) : 0f;

        updateCamera(delta);
        draw(renderHeading, ghostHeading);
    }

    /**
     * The ghost runs on the player's lap clock, so both start together at the line.
     * The player is drawn between the previous tick and the current one, i.e. at (lapTicks - 1 + alpha),
     * so the ghost uses the same moment. Before the first crossing, the ghost waits at the start.
     */
    private float ghostTimeSeconds(float alpha) {
        if (!lapTimer.isLapRunning()) {
            return 0f;
        }
        return (lapTimer.getLapTicks() - 1 + alpha) / GameConstants.PHYSICS_HZ;
    }

    private void physicsStep() {
        car.savePreviousState();
        onTrack = track.isOnTrack(car.position);
        car.update(input, onTrack, GameConstants.PHYSICS_STEP);

        lapTimer.tick();
        recorder.onTick(car);
        int crossed = track.findCrossedCheckpoint(car.previousPosition, car.position);
        if (crossed >= 0) {
            LapTimer.CrossingResult result = lapTimer.onCheckpointCrossed(crossed);
            if (result == LapTimer.CrossingResult.LAP_COMPLETED) {
                onLapCompleted();
            }
            // A completed lap is also the start of the next one, so both begin a fresh recording.
            if (result == LapTimer.CrossingResult.LAP_COMPLETED || result == LapTimer.CrossingResult.LAP_STARTED) {
                applyPendingGhost();
                recorder.start(car);
            }
        }
    }

    private void onLapCompleted() {
        int lapMs = lapTimer.getLastLapMs();
        Ghost lap = recorder.finish(lapMs);

        if (localBestMs == LapTimer.NO_TIME || lapMs < localBestMs) {
            localBestMs = lapMs;
            saveLocally(lap);
        }
        // Beat the ghost? Then race yourself from the next lap on.
        if (ghostPlayer == null || lapMs < ghostPlayer.getGhost().lapTimeMs()) {
            setGhost(new GhostPlayer(lap), "you (this session)");
        }
        // Runs in the background; the verdict appears on the HUD a moment later.
        upload(new LapUpload(GameConstants.TRACK_ID, lapMs, lapTimer.getLastLapCheckpointTimesMs(), lap));
    }

    private void saveLocally(Ghost lap) {
        try {
            long fileBytes = ghostStorage.save(lap);
            int rawBytes = GhostCodec.HEADER_BYTES + lap.sampleCount() * GhostCodec.BYTES_PER_SAMPLE;
            Gdx.app.log(LOG_TAG, String.format("New personal best %s saved: %d samples, %d bytes raw, %d bytes gzipped",
                    LapTimer.formatTime(lap.lapTimeMs()), lap.sampleCount(), rawBytes, fileBytes));
        } catch (IOException e) {
            Gdx.app.error(LOG_TAG, "Could not save ghost", e);
        }
    }

    private void handleKeys() {
        if (Gdx.input.isKeyJustPressed(Keys.F3)) {
            debugOverlay.toggle();
        }
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            resetRace();
        }
        if (Gdx.input.isKeyJustPressed(Keys.TAB)) {
            game.showResults(lastLapMessage);
        }
        if (Gdx.input.isKeyJustPressed(Keys.L) && game.getSession() == null) {
            game.showLogin(null);
        }
    }

    private void readCarInput() {
        input.clear();
        input.throttle = Gdx.input.isKeyPressed(Keys.UP) || Gdx.input.isKeyPressed(Keys.W);
        input.brake = Gdx.input.isKeyPressed(Keys.DOWN) || Gdx.input.isKeyPressed(Keys.S);
        if (Gdx.input.isKeyPressed(Keys.LEFT) || Gdx.input.isKeyPressed(Keys.A)) {
            input.steer += 1f;
        }
        if (Gdx.input.isKeyPressed(Keys.RIGHT) || Gdx.input.isKeyPressed(Keys.D)) {
            input.steer -= 1f;
        }
    }

    private void updateCamera(float delta) {
        cameraTarget.set(renderPosition).mulAdd(car.velocity, CAMERA_LOOK_AHEAD);
        // Exponential smoothing: close a fixed fraction of the gap per second, independent of FPS.
        float t = 1f - (float) Math.exp(-CAMERA_FOLLOW_RATE * delta);
        camera.position.x += (cameraTarget.x - camera.position.x) * t;
        camera.position.y += (cameraTarget.y - camera.position.y) * t;
        camera.update();
    }

    private void draw(float renderHeading, float ghostHeading) {
        ScreenUtils.clear(GRASS);

        worldViewport.apply();
        shapes.setProjectionMatrix(camera.combined);
        // Blending mixes a semi-transparent colour with what is already drawn underneath.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        trackRenderer.draw(shapes, track, lapTimer.getNextCheckpoint());
        if (ghostPlayer != null) {
            carRenderer.draw(shapes, ghostPosition.x, ghostPosition.y, ghostHeading, GHOST_COLOR, GHOST_ALPHA);
        }
        carRenderer.draw(shapes, renderPosition.x, renderPosition.y, renderHeading, PLAYER_COLOR, 1f);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        hudViewport.apply();
        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();
        drawHud();
        batch.end();
    }

    private void drawHud() {
        float top = hudViewport.getWorldHeight() - HUD_MARGIN;
        float lineHeight = font.getLineHeight();

        String current = lapTimer.isLapRunning()
                ? LapTimer.formatTime(lapTimer.getCurrentLapMs())
                : "cross the start line";
        font.draw(batch, "LAP   " + current, HUD_MARGIN, top);
        font.draw(batch, "LAST  " + LapTimer.formatTime(lapTimer.getLastLapMs()), HUD_MARGIN, top - lineHeight);
        font.draw(batch, "BEST  " + LapTimer.formatTime(lapTimer.getBestLapMs()), HUD_MARGIN, top - 2 * lineHeight);
        font.draw(batch, "LAPS  " + lapTimer.getCompletedLaps(), HUD_MARGIN, top - 3 * lineHeight);
        String ghostText = ghostPlayer != null
                ? LapTimer.formatTime(ghostPlayer.getGhost().lapTimeMs()) + "  " + ghostOwner
                : "none yet";
        font.setColor(GHOST_COLOR);
        font.draw(batch, "GHOST " + ghostText, HUD_MARGIN, top - 4 * lineHeight);
        font.setColor(Color.WHITE);

        if (!onTrack) {
            font.setColor(Color.ORANGE);
            font.draw(batch, "OFF TRACK", HUD_MARGIN, top - 5.5f * lineHeight);
            font.setColor(Color.WHITE);
        }

        font.setColor(SERVER_COLOR);
        font.draw(batch, serverStatus, HUD_MARGIN, HUD_MARGIN + 2 * lineHeight);
        font.setColor(Color.WHITE);
        String keys = "Arrows/WASD: drive    R: reset    Tab: leaderboard    F3: debug";
        if (game.getSession() == null) {
            keys += "    L: log in";
        }
        font.draw(batch, keys, HUD_MARGIN, HUD_MARGIN + lineHeight);

        debugOverlay.draw(batch, font, hudViewport.getWorldWidth() - 300f, top,
                car.getSpeed(), lapTimer.getNextCheckpoint(), lapTimer.getCheckpointCount(), onTrack);
    }

    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height);
        hudViewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        disposed = true;
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
