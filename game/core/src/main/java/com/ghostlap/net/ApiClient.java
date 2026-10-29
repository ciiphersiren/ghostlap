package com.ghostlap.net;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.ghostlap.shared.ghost.GhostCodec;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Talks to player-service and race-service over HTTP + JSON.
 *
 * Every method returns immediately. The request runs on HttpClient's background threads; when the
 * response arrives it is parsed there too, and only the finished result is handed to
 * {@code callbackThread} (the game's render thread, via Gdx.app.postRunnable). So the game never
 * freezes while waiting for the network, and callbacks can safely touch game state.
 */
public class ApiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final String playerServiceUrl;
    private final String raceServiceUrl;
    private final Executor callbackThread;

    /** Turns a successful response body into a result object. */
    private interface Parser<T> {
        T parse(JsonValue json);
    }

    public ApiClient(String playerServiceUrl, String raceServiceUrl, Executor callbackThread) {
        this.playerServiceUrl = playerServiceUrl;
        this.raceServiceUrl = raceServiceUrl;
        this.callbackThread = callbackThread;
    }

    public String getPlayerServiceUrl() {
        return playerServiceUrl;
    }

    // ---- player-service ----

    public void register(String username, String password, ApiCallback<Session> callback) {
        authenticate("/api/v1/auth/register", username, password, callback);
    }

    public void login(String username, String password, ApiCallback<Session> callback) {
        authenticate("/api/v1/auth/login", username, password, callback);
    }

    private void authenticate(String path, String username, String password, ApiCallback<Session> callback) {
        JsonValue body = new JsonValue(JsonValue.ValueType.object);
        body.addChild("username", new JsonValue(username));
        body.addChild("password", new JsonValue(password));
        send(post(playerServiceUrl + path, body, null),
                json -> Session.fromToken(json.getLong("playerId"), json.getString("token")),
                callback);
    }

    // ---- race-service ----

    /** POST /api/v1/laps. The ghost is encoded with the shared GhostCodec and sent as base64. */
    public void submitLap(Session session, LapUpload lap, ApiCallback<LapResult> callback) {
        JsonValue times = new JsonValue(JsonValue.ValueType.array);
        for (int ms : lap.checkpointTimesMs()) {
            times.addChild(new JsonValue(ms));
        }
        JsonValue body = new JsonValue(JsonValue.ValueType.object);
        body.addChild("trackId", new JsonValue(lap.trackId()));
        body.addChild("lapTimeMs", new JsonValue(lap.lapTimeMs()));
        body.addChild("checkpointTimesMs", times);
        body.addChild("ghost", new JsonValue(Base64.getEncoder().encodeToString(GhostCodec.encode(lap.ghost()))));

        send(post(raceServiceUrl + "/api/v1/laps", body, session.token()),
                json -> new LapResult(
                        json.getLong("lapId"),
                        "ACCEPTED".equals(json.getString("status")),
                        json.getString("reason", null),
                        json.has("rank") ? json.getInt("rank") : null),
                callback);
    }

    /** GET the fastest accepted ghost on a track. NOT_FOUND if nobody has set a lap yet. */
    public void fetchTopGhost(int trackId, ApiCallback<RemoteGhost> callback) {
        send(get(raceServiceUrl + "/api/v1/tracks/" + trackId + "/ghosts/top"),
                json -> new RemoteGhost(
                        json.getLong("lapId"),
                        json.getString("username"),
                        GhostCodec.decode(Base64.getDecoder().decode(json.getString("ghost")))),
                callback);
    }

    public void fetchLeaderboard(int trackId, int limit, ApiCallback<List<LeaderboardRow>> callback) {
        send(get(raceServiceUrl + "/api/v1/tracks/" + trackId + "/leaderboard?limit=" + limit),
                json -> {
                    List<LeaderboardRow> rows = new ArrayList<>();
                    for (JsonValue row = json.child; row != null; row = row.next) {
                        rows.add(new LeaderboardRow(row.getInt("rank"), row.getString("username"),
                                row.getInt("lapTimeMs"), row.getLong("lapId")));
                    }
                    return rows;
                },
                callback);
    }

    /** Stops the HTTP threads when the game closes. */
    public void shutdown() {
        http.shutdownNow();
    }

    // ---- plumbing ----

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private static HttpRequest post(String url, JsonValue body, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJson(JsonWriter.OutputType.json)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.build();
    }

    /**
     * Sends the request without waiting. The lambda in whenComplete runs later, on an HttpClient
     * background thread, once the response (or an error) arrives.
     */
    private <T> void send(HttpRequest request, Parser<T> parser, ApiCallback<T> callback) {
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    T result = null;
                    ApiError failure = null;
                    if (error != null) {
                        failure = ApiError.unreachable(); // connection refused, timeout, DNS, ...
                    } else if (response.statusCode() / 100 == 2) {
                        try {
                            result = parser.parse(new JsonReader().parse(response.body()));
                        } catch (RuntimeException e) {
                            failure = ApiError.badResponse(response.statusCode());
                        }
                    } else {
                        failure = ApiError.fromResponse(response.statusCode(), response.body());
                    }
                    deliver(callback, result, failure);
                });
    }

    /** Hands the outcome to the game thread. */
    private <T> void deliver(ApiCallback<T> callback, T result, ApiError failure) {
        callbackThread.execute(() -> {
            if (failure == null) {
                callback.onSuccess(result);
            } else {
                callback.onFailure(failure);
            }
        });
    }
}
