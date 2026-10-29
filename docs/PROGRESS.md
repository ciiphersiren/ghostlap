# Progress

## Phase 2: Ghosts (done)

**Codec tests:** `cd shared/ghost-format` then `.\gradlew.bat test` (16 tests)
**Game tests:** `cd game` then `.\gradlew.bat test` (34 tests)

What exists:
- `shared/ghost-format/`: plain Java library (own wrapper, no dependencies).
  - `GameConstants` (moved from the game), plus `GHOST_SAMPLE_RATE_HZ = 30` and `TRACK_ID = 1`.
  - `Ghost` / `GhostSample` records, `GhostCodec.encode/decode`, `GhostFormatException`.
  - Tests: round trip (exact floats), empty ghost, gzip + GHST layout, and 10 corrupt-data cases (null, not gzip, bad magic, bad version, bad/huge count, zero rate, truncated samples, trailing bytes, NaN, cut-off file, flipped byte).
- `game/settings.gradle` uses `includeBuild '../shared/ghost-format'`. `core` depends on `com.ghostlap:ghost-format:1.0.0`, which Gradle substitutes with the source project.
- `ghost/GhostRecorder`: samples every 2nd physics tick (30 Hz) from the start line.
- `ghost/GhostPlayer`: linear position interpolation plus shortest-path heading interpolation.
- `ghost/GhostStorage`: saves and loads `%USERPROFILE%\.ghostlap\ghosts\track-1.ghost` (temp file + atomic move).
- `RaceScreen`: loads the best ghost at startup, records every lap, saves on a new personal best, and draws the ghost in light blue at 45% opacity. The HUD shows `GHOST <time>`.
- `LapSimulationTest` now also checks that the autopilot's ghost passes the future server rules (sample count ±2, speed ≤ MAX_SPEED × 1.1) and writes it to `game/core/build/tmp/autopilot-lap.ghost`.

Measured sizes for one real lap (15.333 s, 461 samples):

| Encoding | Bytes |
|---|---|
| JSON text | 23,345 |
| JSON gzipped | 7,974 |
| Binary, raw (19 + 12 × 461) | 5,551 |
| **Binary, gzipped (the .ghost file)** | **5,285** |
| Base64 of the .ghost file (for the API) | 7,048 |

Verified in the real game: a saved ghost loads at startup (`Loaded ghost: 00:15.333, 461 samples`), and the semi-transparent ghost moves along the track once the start line is crossed. Saving a new best from live keyboard driving still needs a manual check (the logic is covered by tests).

Follow-ups:
- gzip saves only about 5% on raw floats. Delta-encoding or quantising the samples would shrink the file a lot, but that changes the agreed format. Possible format v2, only if needed.

## Phase 1: Game core (done)

**Run it:** `cd game` then `.\gradlew.bat lwjgl3:run`
**Tests:** `cd game` then `.\gradlew.bat test`

Controls: Arrows/WASD to drive, R to reset, F3 for the debug overlay.

What exists:
- `game/` libGDX project (liftoff layout: `core`, `lwjgl3`, `assets`), libGDX 1.14.2, Gradle 9.8.2 wrapper, Java 21 target.
- `track/Track`: closed Catmull-Rom spline through 12 control points, sampled into 300 points; inner/outer edges offset along normals; 8 checkpoints spaced by arc length; on-track test; checkpoint crossing test.
- `car/Car`: arcade physics (forward/lateral split, drag, partial grip, speed-scaled steering, off-track drag).
- `race/LapTimer`: ordered checkpoints, current/last/best lap, timed in physics ticks.
- `race/RaceScreen`: 60 Hz fixed timestep with accumulator, render interpolation, smooth follow camera, HUD.
- `debug/DebugOverlay`: FPS, frame time, heap, speed, next checkpoint, on-track flag.
- Unit tests: `LapTimerTest`, `CarTest`, `TrackTest` (including no self-overlap and minimum curve radius), and `LapSimulationTest`, where an autopilot drives 2 valid laps headlessly (best 15.333 s, track length 9118 units).

Environment notes:
- Only JDK 24/25 are installed, so the build targets Java 21 with `options.release = 21`. LWJGL prints an "Unsupported JNI version" warning on JDK 25. It is harmless, and installing JDK 21 removes it.
- On this machine Java can't reach `services.gradle.org`, but curl can (Maven Central works fine from Java). The Gradle 9.8.2 zip was downloaded with curl, checked against its SHA-256, and placed in `%USERPROFILE%\.gradle\wrapper\dists\`. The wrapper properties pin that checksum.

Notes / follow-ups:
- Tuning: the autopilot lap is within about 1% of the "flat out the whole way" time, so the corners barely need braking. Consider lowering turn rate at high speed after playtesting.
- ~~`GameConstants` moves to `shared/ghost-format` in Phase 2.~~ Done in Phase 2.
- The HUD and debug strings are rebuilt every frame (a little garbage). Revisit in Phase 6 performance work.
