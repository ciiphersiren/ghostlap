package com.ghostlap.net;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostCodec;
import com.ghostlap.shared.ghost.GhostSample;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiClient against a fake server (the JDK's built-in HttpServer), so no backend or Docker is
 * needed. The fake records what it received and answers with a canned response.
 */
class ApiClientTest {

    private HttpServer server;
    private ApiClient client;
    private final AtomicInteger callbacksRun = new AtomicInteger();

    // What the fake server received last.
    private volatile String receivedBody;
    private volatile String receivedAuthorization;

    private final Ghost ghost = new Ghost(1, 30, 15_000, List.of(
            new GhostSample(1f, 2f, 0.5f),
            new GhostSample(3f, 4f, 0.6f)));

    @BeforeEach
    void startFakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); // port 0 = any free port
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        // The real game passes Gdx.app::postRunnable here; the test runs callbacks directly but counts them.
        client = new ApiClient(url, url, runnable -> {
            callbacksRun.incrementAndGet();
            runnable.run();
        });
    }

    @AfterEach
    void stopFakeServer() {
        server.stop(0);
        client.shutdown();
    }

    /** The fake answers {@code path} with this status and body, after an optional delay. */
    private void respond(String path, int status, String body, long delayMs) {
        server.createContext(path, exchange -> {
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            sleep(delayMs);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private void respond(String path, int status, String body) {
        respond(path, status, body, 0);
    }

    @Test
    void loginSendsCredentialsAndReadsSessionFromToken() throws Exception {
        String token = TestJwt.token("reet", Instant.parse("2030-01-01T00:00:00Z"));
        respond("/api/v1/auth/login", 200, "{\"playerId\": 7, \"token\": \"" + token + "\"}");

        Session session = Await.success(cb -> client.login("reet", "pass\"word", cb));

        assertThat(session.playerId()).isEqualTo(7);
        assertThat(session.username()).isEqualTo("reet");
        assertThat(session.token()).isEqualTo(token);
        assertThat(session.expiresAt()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
        JsonValue sent = new JsonReader().parse(receivedBody);
        assertThat(sent.getString("username")).isEqualTo("reet");
        assertThat(sent.getString("password")).isEqualTo("pass\"word"); // quotes are escaped properly
    }

    @Test
    void callsReturnImmediatelyAndAnswerThroughTheCallbackThread() throws Exception {
        String token = TestJwt.token("reet", Instant.now().plusSeconds(3600));
        respond("/api/v1/auth/login", 200, "{\"playerId\": 7, \"token\": \"" + token + "\"}", 500);

        long start = System.nanoTime();
        Session session = Await.success(cb -> {
            client.login("reet", "password123", cb);
            // The server takes 500 ms, but login() itself must not wait for it.
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(200);
        });

        assertThat(session.username()).isEqualTo("reet");
        assertThat(callbacksRun).hasValue(1);
    }

    @Test
    void wrongPasswordIsUnauthorizedWithTheServersMessage() throws Exception {
        respond("/api/v1/auth/login", 401,
                "{\"status\": 401, \"title\": \"Unauthorized\", \"detail\": \"Invalid username or password\"}");

        ApiError error = Await.<Session>failure(cb -> client.login("reet", "wrong", cb));

        assertThat(error.kind()).isEqualTo(ApiError.Kind.UNAUTHORIZED);
        assertThat(error.message()).isEqualTo("Invalid username or password");
    }

    @Test
    void validationErrorShowsTheFieldThatFailed() throws Exception {
        respond("/api/v1/auth/register", 400,
                "{\"status\": 400, \"detail\": \"Request validation failed\", "
                        + "\"errors\": {\"password\": \"size must be between 8 and 72\"}}");

        ApiError error = Await.<Session>failure(cb -> client.register("reet", "short", cb));

        assertThat(error.kind()).isEqualTo(ApiError.Kind.BAD_REQUEST);
        assertThat(error.message()).isEqualTo("password: size must be between 8 and 72");
    }

    @Test
    void takenUsernameIsConflict() throws Exception {
        respond("/api/v1/auth/register", 409, "{\"status\": 409, \"detail\": \"Username 'reet' is already taken\"}");

        ApiError error = Await.<Session>failure(cb -> client.register("reet", "password123", cb));

        assertThat(error.kind()).isEqualTo(ApiError.Kind.CONFLICT);
        assertThat(error.message()).contains("already taken");
    }

    @Test
    void serverDownIsUnreachable() throws Exception {
        int port = server.getAddress().getPort();
        server.stop(0); // nothing listens on the port any more
        ApiClient offline = new ApiClient("http://127.0.0.1:" + port, "http://127.0.0.1:" + port, Runnable::run);

        ApiError error = Await.<Session>failure(cb -> offline.login("reet", "password123", cb));

        assertThat(error.kind()).isEqualTo(ApiError.Kind.UNREACHABLE);
        offline.shutdown();
    }

    @Test
    void submitLapSendsTokenAndLapAsJson() throws Exception {
        respond("/api/v1/laps", 201, "{\"lapId\": 12, \"status\": \"ACCEPTED\", \"rank\": 3}");
        Session session = new Session(7, "reet", "the.jwt.token", Instant.now().plusSeconds(3600));
        LapUpload lap = new LapUpload(1, 15_000, new int[]{1000, 2000, 3000, 4000, 5000, 6000, 7000, 15_000}, ghost);

        LapResult result = Await.success(cb -> client.submitLap(session, lap, cb));

        assertThat(result).isEqualTo(new LapResult(12, true, null, 3));
        assertThat(receivedAuthorization).isEqualTo("Bearer the.jwt.token");
        JsonValue sent = new JsonReader().parse(receivedBody);
        assertThat(sent.getInt("trackId")).isEqualTo(1);
        assertThat(sent.getInt("lapTimeMs")).isEqualTo(15_000);
        assertThat(sent.get("checkpointTimesMs").asIntArray()).containsExactly(lap.checkpointTimesMs());
        // The ghost travels as base64 of the GhostCodec bytes and comes back identical.
        assertThat(GhostCodec.decode(Base64.getDecoder().decode(sent.getString("ghost")))).isEqualTo(ghost);
    }

    @Test
    void rejectedLapCarriesTheReason() throws Exception {
        respond("/api/v1/laps", 201, "{\"lapId\": 13, \"status\": \"REJECTED\", \"reason\": \"TOO_FAST\"}");
        Session session = new Session(7, "reet", "the.jwt.token", Instant.now().plusSeconds(3600));

        LapResult result = Await.success(cb -> client.submitLap(session, new LapUpload(1, 15_000, new int[8], ghost), cb));

        assertThat(result).isEqualTo(new LapResult(13, false, "TOO_FAST", null));
    }

    @Test
    void topGhostIsDownloadedAndDecoded() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(GhostCodec.encode(ghost));
        respond("/api/v1/tracks/1/ghosts/top", 200,
                "{\"lapId\": 4, \"username\": \"alice\", \"trackId\": 1, \"lapTimeMs\": 15000, \"ghost\": \"" + base64 + "\"}");

        RemoteGhost remote = Await.success(cb -> client.fetchTopGhost(1, cb));

        assertThat(remote.lapId()).isEqualTo(4);
        assertThat(remote.username()).isEqualTo("alice");
        assertThat(remote.ghost()).isEqualTo(ghost);
    }

    @Test
    void noGhostOnServerIsNotFound() throws Exception {
        respond("/api/v1/tracks/1/ghosts/top", 404, "{\"status\": 404, \"detail\": \"No accepted laps on track 1 yet\"}");

        ApiError error = Await.<RemoteGhost>failure(cb -> client.fetchTopGhost(1, cb));

        assertThat(error.kind()).isEqualTo(ApiError.Kind.NOT_FOUND);
    }

    @Test
    void corruptGhostFromServerIsAnError() throws Exception {
        respond("/api/v1/tracks/1/ghosts/top", 200,
                "{\"lapId\": 4, \"username\": \"alice\", \"ghost\": \"AAAA\"}");

        ApiError error = Await.<RemoteGhost>failure(cb -> client.fetchTopGhost(1, cb));

        assertThat(error.kind()).isEqualTo(ApiError.Kind.SERVER_ERROR);
    }

    @Test
    void leaderboardRowsAreParsed() throws Exception {
        respond("/api/v1/tracks/1/leaderboard", 200, """
                [{"rank": 1, "username": "alice", "lapTimeMs": 15000, "lapId": 4},
                 {"rank": 2, "username": "bob", "lapTimeMs": 15500, "lapId": 9}]
                """);

        List<LeaderboardRow> rows = Await.success(cb -> client.fetchLeaderboard(1, 10, cb));

        assertThat(rows).containsExactly(
                new LeaderboardRow(1, "alice", 15_000, 4),
                new LeaderboardRow(2, "bob", 15_500, 9));
    }

    @Test
    void nonJsonErrorPageStillGivesAMessage() throws Exception {
        respond("/api/v1/tracks/1/leaderboard", 502, "<html>Bad Gateway</html>");

        ApiError error = Await.<List<LeaderboardRow>>failure(cb -> client.fetchLeaderboard(1, 10, cb));

        assertThat(error.kind()).isEqualTo(ApiError.Kind.SERVER_ERROR);
        assertThat(error.message()).isEqualTo("Server error (HTTP 502)");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
