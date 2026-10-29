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
