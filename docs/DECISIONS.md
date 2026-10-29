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
