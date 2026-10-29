# Design Decisions

One line per decision: what was chosen and why.

## Phase 1: Game core
- **Hand-written gdx-liftoff layout:** `game/` uses liftoff's standard `core` + `lwjgl3` + `assets` structure, written by hand because liftoff is a GUI tool. Same result, and every file is understood.
- **JDK 25 compiles to Java 21:** only JDK 24/25 are installed, so the build sets `options.release = 21`. The bytecode and API level are Java 21 whichever JDK runs Gradle.
- **GameConstants lives in `game/core` for now:** it moves to `shared/ghost-format` in Phase 2, when the server needs the same numbers.
- **Lap time counted in physics ticks, not seconds:** integers are exact and deterministic. ms = ticks × 1000 / 60, so laps are measured to about 16.7 ms.
- **Checkpoints spaced by arc length, not spline `t`:** equal steps in `t` are not equal distances along a Catmull-Rom spline.
- **Checkpoint detection is a segment–segment intersection** between the car's movement this tick and the checkpoint line, so a fast car can't skip over a line between two ticks.
- **Checkpoint detection lines stick out half a track width past each edge,** so running slightly wide at a checkpoint doesn't void the lap. Cutting the course still misses checkpoints.
- **Off-track test is distance to the centerline polyline ≤ half the width:** simple, and cheap enough (300 segments per tick).
- **Lateral grip is applied per tick (25% of sideways speed removed):** this is valid only because the timestep is fixed.
- **`Math.cos/sin` in physics, not `MathUtils` lookup tables:** gives more precise and repeatable results, which matters once the server validates ghosts.
- **Camera uses exponential smoothing (`1 - e^(-k·dt)`) with velocity look-ahead:** the smoothing is the same at any frame rate.
- **Wrapper pins `distributionSha256Sum`:** the wrapper refuses a Gradle zip that has been tampered with or corrupted.
- **Headless autopilot test (`LapSimulationTest`):** proves a valid lap can be driven with the real physics and tick order, without opening a window.
- **R resets the race:** a small dev convenience, so you never have to drive back to the track.

## Phase 2: Ghosts
- **`shared/ghost-format` has zero dependencies (no libGDX):** race-service can use the same codec and constants without pulling in a game engine.
- **Package `com.ghostlap.shared` (`GameConstants`) and `com.ghostlap.shared.ghost` (codec):** avoids two builds putting classes in the same package.
- **Composite build (`includeBuild`) instead of publishing a jar:** a change to the codec takes effect in the game on the next build, with no version bumps or local Maven repo.
- **The library has its own Gradle wrapper and tests:** run with `cd shared/ghost-format; .\gradlew.bat test`. The game's `test` task does not run an included build's tests.
- **`Ghost` and `GhostSample` are Java records:** immutable, with `equals` for free, so the round-trip tests compare whole objects exactly.
- **Every decode failure becomes `GhostFormatException`:** the server maps exactly one exception type to `BAD_GHOST`.
- **Decode guards against hostile input:** sample count is capped (`MAX_SAMPLES`) before allocating, floats must be finite, and trailing bytes are rejected. Reading to the end also makes gzip check its CRC.
- **Sample `i` = lap tick `2i`, including tick 0 at the start line:** sample count = lapTicks/2 + 1, which meets the server's ±2 rule.
- **`LapTimer.onCheckpointCrossed` returns a `CrossingResult`:** the screen knows exactly when a lap starts or completes, without comparing counters.
- **The ghost runs on the player's lap clock at `(lapTicks - 1 + alpha)`:** the same moment the player's interpolated car is drawn, so the two are compared fairly frame by frame.
- **A new best replaces the ghost immediately:** your next lap races your latest best.
- **Saved to `%USERPROFILE%\.ghostlap\ghosts\track-<id>.ghost` with `java.nio`, not `Gdx.files`:** outside the repo, and testable without starting libGDX.
- **Save writes a temp file, then atomically moves it into place:** a crash mid-save can't destroy the previous best.
- **A corrupt or missing ghost file is logged and ignored:** the game still starts, and the next best lap overwrites the file.
- **Storage tests use `build/tmp` instead of JUnit `@TempDir`:** on this Windows machine, deleting a folder right after replacing a file in it failed in 2 of 5 runs ("directory not empty"), because a background process briefly held the file.

## Phase 3: Backend
- **Spring Boot 4.1.2 (latest stable on start.spring.io), projects generated from start.spring.io:** Boot 4 split the starters into modules (`spring-boot-starter-webmvc`, `-flyway`, ...), so generating them avoids guessing names.
- **Java 21 via `options.release`, not the generated toolchain:** the toolchain would look for an installed JDK 21, and only 24/25 are installed. Same approach as the game.
- **JWTs use Spring Security's built-in OAuth2 resource server (Nimbus), not an extra JWT library:** it both signs (`NimbusJwtEncoder`) and checks (`NimbusJwtDecoder`) HS256 tokens, and plugs straight into the security filter chain.
- **Token claims: `sub` = player id, plus a `username` claim:** race-service can show names on the leaderboard without calling player-service.
- **race-service also requires `iss = ghostlap` and a non-empty `username` claim:** a token missing them is rejected with 401 instead of failing later.
- **The services never call each other:** the signed token carries everything race-service needs. One service being down doesn't break the other.
- **player-service has no Redis dependency:** it doesn't use Redis, and an unused connection would only add a failing health check.
- **One Postgres login per database (`players_app`, `race_app`), created by an init script:** each service can only connect to its own database, so data ownership is enforced, not just agreed.
- **No foreign key from `laps.player_id` to players:** they are in different databases. The username is copied onto each lap at submit time.
- **Flyway owns the schema; Hibernate runs with `ddl-auto=validate`:** the SQL is reviewed and versioned, and the app refuses to start if the entities don't match the tables.
- **Usernames are unique ignoring case** (unique index on `LOWER(username)`), so "Reet" and "reet" can't both exist.
- **Login gives the same 401 message for unknown user and wrong password:** it doesn't tell an attacker which usernames exist.
- **Passwords are limited to 8–72 characters:** BCrypt ignores everything after 72 bytes.
- **Security errors (401) are written as ProblemDetail by a custom entry point:** they come from the security filters, which run before the normal exception handler.
- **A rejected lap is a normal `201` with `status: REJECTED`, not an HTTP error:** the request was fine; the lap just wasn't believable. It is stored with its reason code and a human-readable detail.
- **Validation runs the cheap rules first (TOO_FAST, CHECKPOINT_ORDER), then decodes the ghost:** no gunzipping for laps that are obviously invalid.
- **`checkpointTimesMs` has 8 entries, and the last one is the finish line, equal to `lapTimeMs`:** one time per checkpoint, in crossing order. The game must send exactly this in Phase 4.
- **BAD_GHOST also covers a ghost whose header doesn't match the lap** (other track, other lap time, or a sample rate other than 30 Hz). A lower rate would hide movement from the speed check.
- **Track 1 minimum lap time = 13,000 ms:** centerline 9,118 units at 600 units/s = 15.2 s; a racing line cuts corners, so we allow about 15% below that.
- **Ghosts are stored as `bytea` in their own table (`lap_ghosts`), only for accepted laps:** listing laps never loads the binary data, and cheaters can't fill the database with junk. A plain `byte[]` maps to `bytea`; `@Lob` would use Postgres large objects instead.
- **Postgres is written first, in one transaction (lap + ghost); Redis is updated after the commit:** if Redis fails, the lap is safe and the response just has no rank. A rebuild fixes the board.
- **Redis leaderboard = a sorted set per track (member = playerId, score = best lap ms) plus a hash with `lapId:username` per player:** one row per player, ranks in O(log n), and the top 10 in two round trips.
- **Leaderboard rebuild uses temporary keys and RENAME:** readers never see a half-built board. It runs automatically at startup when a track's board is missing from Redis.
- **The top ghost is read from Postgres, not Redis:** it needs the ghost bytes, which only Postgres has, and Postgres is the source of truth.
- **Leaderboards and ghosts are public GETs; submitting a lap needs a token.**
- **Testcontainers with `postgres:16-alpine` and `redis:7-alpine`, matching docker-compose:** tests run against the same databases as production, not H2 or mocks.
- **One shared Spring test context per service (a base `IntegrationTest` class):** containers start once per test run, not once per test class.
- **`@AutoConfigureMetrics` in tests:** Spring Boot tests switch metrics export off by default, which hides `/actuator/prometheus`.
- **Docker images are two-stage (JDK builds, JRE runs) and run as a non-root user.**
- **Docker build context is the repo root:** race-service needs `shared/ghost-format` to build. `.dockerignore` keeps `game/`, `infra/` (with `.env`) and build output out.
- **Gradle cache mount with `sharing=locked`:** compose builds both images in parallel; with a shared, unlocked cache the second build timed out waiting for the Gradle download lock.
- **Postgres is published on host port 5433:** this laptop already runs a local PostgreSQL on 5432. Containers still talk to each other on 5432.
- **Postgres and Redis ports are bound to 127.0.0.1 only;** Redis has no password because it is not reachable from outside this machine.
- **Request IDs and the custom lap metrics are left for Phase 5** (production polish), as the phase checklist says.

## Phase 4: Integration
- **HTTP with the JDK's `java.net.http.HttpClient` (`sendAsync`), not libGDX's `Gdx.net`:** both are asynchronous, but HttpClient has connect/request timeouts built in and works without a running libGDX app, so `ApiClient` is unit-tested against a fake server. No new dependency.
- **Network results reach the game through one door: `Gdx.app.postRunnable`:** HttpClient answers on its own threads; the callback is queued and runs on the render thread at the start of the next frame, so game state is only ever touched by one thread (no locks).
- **Responses are parsed (and ghosts decoded) on the background thread;** only the finished object is posted to the render thread.
- **JSON with libGDX's `JsonValue`/`JsonReader`:** already part of libGDX. Building requests with `JsonValue` escapes quotes in usernames and passwords correctly.
- **The token stays in memory only (`Session` in `GhostLapGame`):** closing the game logs out, and no secret is written to disk.
- **The client reads `username` and `exp` from the JWT payload:** it can show the right name and skip a request with an expired token. It can't verify the signature (only the servers know the secret), so the server still decides.
- **UI skin built in code from a 1×1 white pixel and libGDX's default font:** no skin files (json/atlas/png) to ship or explain.
- **Screens are switched at the start of the next frame (postRunnable) and the old one is disposed:** a screen never keeps drawing after its resources are freed.
- **Each screen ignores network answers that arrive after it was disposed** (`disposed` flag).
- **Screens: Login → Race ⇄ Results/Leaderboard (Tab).** The separate Menu screen from CLAUDE.md is not built yet; Login and Results cover its job for now.
- **"Play offline" on the login screen:** the game is fully playable without the backend.
- **The race uses the fastest ghost available:** the server's top ghost, or the local best if that is strictly faster. A ghost that arrives mid-lap is swapped in at the next lap start, so it doesn't jump.
- **Local ghosts are stored per account (`~/.ghostlap/ghosts/players/<username>/`);** offline play keeps the old folder. Two accounts on one computer don't overwrite each other's personal best.
- **Every completed lap is uploaded** (not only personal bests): the server keeps each player's best for the leaderboard, and the player sees ACCEPTED/REJECTED and their rank for every lap.
- **`checkpointTimesMs` = lap ticks at checkpoints 1..7 and then at the finish line, converted to ms.** The last entry is the lap time, as the API requires.
- **Failure handling:** server unreachable → keep racing, keep the lap and retry it at the next race start. 401 or expired token → log out, keep the lap, "press L to log in again", and send it after login. Rejected lap → shown with its reason code.
- **Only the most recent unsent lap is kept for retry:** simple, and the leaderboard only counts a player's best anyway.
- **Server URLs come from env vars `GHOSTLAP_PLAYER_URL` / `GHOSTLAP_RACE_URL`** (defaults: localhost:8081/8082), read by the desktop launcher.
- **`LiveBackendTest` runs only with `GHOSTLAP_LIVE_TEST=true`:** the normal `gradlew test` must not need Docker. It uploads a lap driven by the real game physics (the autopilot, now a shared test helper) and checks that a second account downloads it.

## Phase 5: Production polish
- **Custom metrics live in one class (`LapMetrics`) using Micrometer, not the Prometheus client directly:** Micrometer is Spring Boot's metrics API, and the Prometheus registry turns `ghostlap.laps.rejected` into `ghostlap_laps_rejected_total`.
- **`ghostlap_laps_submitted_total` counts every stored lap, accepted or rejected;** `ghostlap_laps_rejected_total{reason}` has one series per reason. Requests that fail before validation (400, 401, 404) are not laps and are not counted.
- **All five reason counters are registered at 0 at startup:** each line exists in Grafana before the first cheat, and `increase()` works from the first rejection.
- **The validation timer and HTTP requests publish histogram buckets:** p95 is computed in Prometheus with `histogram_quantile`, so it can be combined across instances. Pre-computed percentiles in the app can't be.
- **Request IDs come from a servlet filter that runs before Spring Security:** even 401s get an ID. The ID goes into the logging MDC (`%X{requestId}` in `logging.pattern.level`) and the `X-Request-Id` response header.
- **A caller's `X-Request-Id` is kept only if it is 1–64 letters, digits or dashes:** otherwise someone could inject fake log lines with newlines.
- **One access-log line per API request ("POST /api/v1/laps -> 201 (15 ms)"), but not for `/actuator`:** Prometheus scrapes every 5 s and would flood the logs.
- **The request-ID filter is copied into both services, not shared:** the services share no Spring code (only ghost-format), and it's one small class.
- **Distributed tracing (traces) is not added:** the services never call each other, so a trace would always be one span long. Request IDs give the same "find all logs of one request" benefit here.
- **Prometheus scrapes every 5 s; dashboard panels use `[1m]` windows:** live enough while playing, with enough samples per window for smooth lines.
- **The dashboard and the data source are provisioned from files in `infra/grafana`;** they appear on `docker compose up` and live in git.
- **Grafana allows anonymous read-only viewing; the admin password comes from `GRAFANA_ADMIN_PASSWORD`.** Prometheus and Grafana are bound to 127.0.0.1.
- **Images pinned to exact versions (`prom/prometheus:v3.5.0`, `grafana/grafana:12.1.0`):** the dashboard JSON is tested against a known Grafana version.
- **The cheater client is a `game/cheater` module that reuses the game's `ApiClient`:** it really is "a hacked game client", with no second HTTP/JSON implementation. Run with `.\gradlew.bat cheater:run`.
- **The cheater only sends laps that must be rejected,** so it never pollutes the leaderboard. It prints "UNEXPECTED" if a reason doesn't match.
- **CI: one parallel job per project (matrix), then a Docker job that needs all of them to pass:** a failure shows exactly which project broke, and images are only built from tested code.
- **CI uses `./gradlew build` (compile + test + package), the same command developers run;** JDK 21 from Temurin, and `gradle/actions/setup-gradle` for caching and wrapper validation.
- **The service tests run with real Postgres/Redis in CI** (Testcontainers on GitHub's Ubuntu runners, which have Docker).
- **CI checks `docker-compose.yml` with the `.env.example` values,** so no secrets are needed in GitHub.
- **The `gradlew` scripts are marked executable in git (`git update-index --chmod=+x`):** Windows doesn't track that flag, and Linux CI would otherwise fail with "Permission denied".
