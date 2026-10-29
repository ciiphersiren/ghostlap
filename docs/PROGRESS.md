# Progress

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
- `GameConstants` moves to `shared/ghost-format` in Phase 2.
- The HUD and debug strings are rebuilt every frame (a little garbage). Revisit in Phase 6 performance work.
