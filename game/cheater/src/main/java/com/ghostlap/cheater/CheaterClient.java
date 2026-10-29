package com.ghostlap.cheater;

import com.ghostlap.net.ApiCallback;
import com.ghostlap.net.ApiClient;
import com.ghostlap.net.ApiError;
import com.ghostlap.net.LapResult;
import com.ghostlap.net.LapUpload;
import com.ghostlap.net.Session;
import com.ghostlap.shared.GameConstants;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostSample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A "hacked game client": registers an account and submits laps that break each anti-cheat rule,
 * so ghostlap_laps_rejected_total{reason} moves on the Grafana dashboard.
 *
 *   cd game
 *   .\gradlew.bat cheater:run --args="--rounds 20 --delay-ms 500"
 *
 * Every lap starts from an honest-looking one (a car circling at 500 units/s, sampled at 30 Hz like
 * the game) and then tampers with one thing. None of these laps should ever be accepted.
 */
public final class CheaterClient {

    private static final int HONEST_LAP_MS = 16_000;
    private static final float SPEED = 500f; // units per second, below MAX_SPEED (600)

    /** One kind of cheat: a name, the reason we expect, and how to build the fake lap. */
    private record Cheat(String name, String expectedReason, LapUpload lap) {
    }

    private CheaterClient() {
    }

    public static void main(String[] args) throws Exception {
        int rounds = intArg(args, "--rounds", 10);
        int delayMs = intArg(args, "--delay-ms", 1000);
        String playerUrl = env("GHOSTLAP_PLAYER_URL", "http://localhost:8081");
        String raceUrl = env("GHOSTLAP_RACE_URL", "http://localhost:8082");

        // Callbacks run directly on the HTTP thread; we simply wait for each one (no game loop here).
        ApiClient api = new ApiClient(playerUrl, raceUrl, Runnable::run);
        try {
            String username = "cheater_" + UUID.randomUUID().toString().substring(0, 6);
            Session session = await(cb -> api.register(username, "password123", cb));
            System.out.printf("Registered %s. Sending %d rounds of fake laps to %s%n%n", username, rounds, raceUrl);

            Map<String, Integer> outcomes = new TreeMap<>();
            for (int round = 1; round <= rounds; round++) {
                for (Cheat cheat : cheats()) {
                    LapResult result = await(cb -> api.submitLap(session, cheat.lap(), cb));
                    String outcome = result.accepted() ? "ACCEPTED (!)" : result.reason();
                    String check = cheat.expectedReason().equals(result.reason()) ? "as expected" : "UNEXPECTED";
                    System.out.printf("round %2d  %-24s -> %-22s %s%n", round, cheat.name(), outcome, check);
                    outcomes.merge(outcome, 1, Integer::sum);
                }
                Thread.sleep(delayMs);
            }
            System.out.println();
            System.out.println("Summary: " + outcomes);
        } finally {
            api.shutdown();
        }
    }

    private static List<Cheat> cheats() {
        List<Cheat> cheats = new ArrayList<>();

        // 1. Claim an impossible time (the ghost is consistent with it, so only TOO_FAST catches it).
        int fakeMs = 8_000;
        cheats.add(new Cheat("impossible time", "TOO_FAST",
                lap(fakeMs, splits(fakeMs), circle(fakeMs, samplesFor(fakeMs)))));

        // 2. Teleport: one sample jumps 400 units sideways (400 units in 1/30 s = 12,000 units/s).
        List<GhostSample> samples = new ArrayList<>(circle(HONEST_LAP_MS, samplesFor(HONEST_LAP_MS)).samples());
        GhostSample s = samples.get(200);
        samples.set(200, new GhostSample(s.x() + 400, s.y(), s.heading()));
        cheats.add(new Cheat("teleport", "SPEED_EXCEEDED",
                lap(HONEST_LAP_MS, splits(HONEST_LAP_MS), ghost(HONEST_LAP_MS, samples))));

        // 3. Skip checkpoints: only 5 split times instead of 8.
        int[] fiveSplits = {2000, 4000, 8000, 12_000, HONEST_LAP_MS};
        cheats.add(new Cheat("skipped checkpoints", "CHECKPOINT_ORDER",
                lap(HONEST_LAP_MS, fiveSplits, circle(HONEST_LAP_MS, samplesFor(HONEST_LAP_MS)))));

        // 4. Checkpoints in the wrong order.
        int[] shuffled = splits(HONEST_LAP_MS);
        int swap = shuffled[2];
        shuffled[2] = shuffled[3];
        shuffled[3] = swap;
        cheats.add(new Cheat("checkpoints out of order", "CHECKPOINT_ORDER",
                lap(HONEST_LAP_MS, shuffled, circle(HONEST_LAP_MS, samplesFor(HONEST_LAP_MS)))));

        // 5. Cut the ghost short: it only covers half the lap.
        cheats.add(new Cheat("half a ghost", "SAMPLE_COUNT_MISMATCH",
                lap(HONEST_LAP_MS, splits(HONEST_LAP_MS), circle(HONEST_LAP_MS, samplesFor(HONEST_LAP_MS) / 2))));

        // 6. Edit the lap time but not the ghost: the ghost's header still says 16 s.
        int editedMs = 15_000;
        cheats.add(new Cheat("edited lap time", "BAD_GHOST",
                lap(editedMs, splits(editedMs), circle(HONEST_LAP_MS, samplesFor(HONEST_LAP_MS)))));
        return cheats;
    }

    // ---- building fake laps ----

    private static LapUpload lap(int lapTimeMs, int[] checkpointTimesMs, Ghost ghost) {
        return new LapUpload(GameConstants.TRACK_ID, lapTimeMs, checkpointTimesMs, ghost);
    }

    /** What the game records: one sample every 2 physics ticks, starting at tick 0. */
    private static int samplesFor(int lapTimeMs) {
        return lapTimeMs * GameConstants.GHOST_SAMPLE_RATE_HZ / 1000 + 1;
    }

    /** One split per checkpoint, evenly spaced, the last at the finish line. */
    private static int[] splits(int lapTimeMs) {
        int[] times = new int[GameConstants.CHECKPOINT_COUNT];
        for (int i = 0; i < times.length; i++) {
            times[i] = lapTimeMs * (i + 1) / GameConstants.CHECKPOINT_COUNT;
        }
        return times;
    }

    /** A car driving one circle at SPEED in lapTimeMs. */
    private static Ghost circle(int lapTimeMs, int sampleCount) {
        double seconds = lapTimeMs / 1000.0;
        double radius = SPEED * seconds / (2 * Math.PI);
        List<GhostSample> samples = new ArrayList<>();
        for (int i = 0; i < sampleCount; i++) {
            double angle = 2 * Math.PI * ((double) i / GameConstants.GHOST_SAMPLE_RATE_HZ) / seconds;
            samples.add(new GhostSample((float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle)),
                    (float) (angle + Math.PI / 2)));
        }
        return ghost(lapTimeMs, samples);
    }

    private static Ghost ghost(int lapTimeMs, List<GhostSample> samples) {
        return new Ghost(GameConstants.TRACK_ID, GameConstants.GHOST_SAMPLE_RATE_HZ, lapTimeMs, samples);
    }

    // ---- plumbing ----

    /** Waits for an async ApiClient call; stops the program with a clear message if it fails. */
    private static <T> T await(Consumer<ApiCallback<T>> call) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        call.accept(new ApiCallback<>() {
            @Override
            public void onSuccess(T result) {
                done.complete(result);
            }

            @Override
            public void onFailure(ApiError error) {
                done.completeExceptionally(new IllegalStateException(
                        error.kind() + ": " + error.message() + " (is docker compose running?)"));
            }
        });
        return done.get(15, TimeUnit.SECONDS);
    }

    private static int intArg(String[] args, String name, int defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return defaultValue;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
