# Performance

Everything here was measured, not estimated. The rule throughout: **measure first, change one thing,
measure again**, and when the machine is noisy, compare back to back (A/B).

**Machine:** i5-12450H (8 cores / 12 threads), 16 GB RAM, Intel UHD integrated graphics, Windows 11,
JDK 25 (compiling to Java 21). The backend runs in Docker Desktop (WSL2) on the same laptop, alongside
Prometheus, Grafana and k6, so all of them compete for the same CPU.

---

## 1. Game

### How it was measured

**Benchmark mode** (`GHOSTLAP_BENCHMARK=1`, code in `game/core/.../perf/Benchmark.java`):
- Skips login and starts the race offline; the car drives itself with `AutoDriver` (the same autopilot as the tests), so every run does the same work.
- **Vsync off and no FPS cap,** so frame time shows our real cost instead of the monitor's 16.7 ms.
- **5 s warm-up** (the JIT compiler optimises the hot code first), then **20 s measured.**
- Per frame it records:
  - **frame time:** start of one frame to the start of the next. FPS is based on this, and it includes the GPU driver and the OS.
  - **`render()` CPU time:** only our update + draw code. This is the most stable number.
  - **bytes allocated on the render thread** (`ThreadMXBean.getCurrentThreadAllocatedBytes`): garbage the GC has to clean up later.
  - **GC collections and pause time** during the 20 s (`GarbageCollectorMXBean`).
  - **draw calls, vertices and texture bindings** (libGDX `GLProfiler`).
- The measuring itself allocates nothing: times go into preallocated arrays. Percentiles use the nearest-rank method.

**Startup** (`perf/StartupTimer.java`) logs three phases once the first frame has been drawn:
JVM start → `main()` (from `RuntimeMXBean.getUptime()`), `main()` → `create()` (LWJGL natives, window,
OpenGL context), and `create()` → first frame (our loading and the first draw).

Run it yourself:
```powershell
cd game; $env:GHOSTLAP_BENCHMARK="1"; .\gradlew.bat lwjgl3:run; Remove-Item Env:GHOSTLAP_BENCHMARK
```

The first run after a build is "cold" (OS file cache, CPU boost) and noticeably slower, so tables use
**medians** of 3–4 runs.

### Baseline (before)

| Metric | Run 1 (cold) | Run 2 | Run 3 | **Median** |
|---|---|---|---|---|
| FPS (uncapped) | 923 | 1,225 | 1,171 | **1,171** |
| `render()` avg / p99 | 0.508 / 2.023 ms | 0.389 / 1.415 ms | 0.429 / 1.455 ms | **0.429 / 1.455 ms** |
| Allocated per frame | 2,478 B | 2,808 B | 2,848 B | **2,808 B (3.3 MB/s)** |
| GC collections (20 s) | 1 | 1 | 1 | **1** |
| Draw calls / vertices / texture binds per frame | 3 / 6,648 / 1 | 3 / 6,714 / 1 | 3 / 6,714 / 1 | **3 / 6,714 / 1** |
| Startup total | 1,779 ms | 1,633 ms | 1,390 ms | **1,633 ms** |
| … `create()` → first frame | 603 ms | 624 ms | 494 ms | **603 ms** |

What the numbers pointed at:
- **2.8 KB of garbage per frame:** the HUD built new strings every frame (`"LAP " + ...`, and `String.format` inside `LapTimer.formatTime`, which creates several objects per call). The F3 overlay did the same.
- **6,714 vertices per frame:** the whole track (300 segments: road quads + both edges) was sent to the GPU every frame, even though the camera only shows part of it.
- **1 texture binding per frame:** the only texture is the font page.
- **Startup:** I timed the steps inside `create()` with temporary probes:

  | Step | Time |
  |---|---|
  | UI skin | 60–68 ms |
  | **creating the `HttpClient`** | **248–268 ms** |
  | `RaceScreen` constructor | 63–66 ms |
  | first render | 15 ms |

### Change 1: no per-frame garbage (HUD and F3 overlay)

- `LapTimer.appendTime(StringBuilder, ms)` writes `mm:ss.mmm` digit by digit, with no `String.format`.
- `RaceScreen.drawHud()` and `DebugOverlay` reuse one `StringBuilder` and pass it straight to `font.draw` (which accepts any `CharSequence`).

| (median of 3) | Before | After |
|---|---|---|
| Allocated per frame | 2,808 B | **7 B** |
| Allocation rate | 3.3 MB/s | **0.01 MB/s** |
| GC collections in 20 s | 1 | **0** |
| `render()` avg | 0.429 ms | 0.416 ms (within noise) |

Honest reading: at 3.3 MB/s the G1 collector coped easily (one ~8 ms young collection per 20 s), so FPS
didn't change. The win is **no GC pauses during long sessions**, which matters more as a game grows. The
remaining 7 B/frame is the ghost recorder storing 30 samples per second, which the game needs.

### Change 2: track culling

`TrackRenderer` computes the camera's visible rectangle (plus a 20-unit margin) and skips any segment
whose bounding box doesn't overlap it: a cheap min/max test on 4 points per segment, with no allocations.

The first runs after this change showed *lower* FPS (943 vs 1,171), while `render()` time went down.
Fewer vertices can't make frames slower, so I suspected background load (Docker, a browser video on the
shared integrated GPU). I ran an **A/B test: alternating unculled/culled builds back to back, 3 rounds:**

| (median of 3 alternating runs) | Unculled | Culled | Change |
|---|---|---|---|
| Vertices per frame | 6,714 | 2,299 | **−66%** |
| Draw calls per frame | 3 | 2 | −1 (ShapeRenderer's buffer no longer overflows) |
| `render()` avg | 0.437 ms | 0.345 ms | **−21%** |
| `render()` p50 | 0.351 ms | 0.253 ms | −28% |
| `render()` p99 | 1.553 ms | 1.371 ms | −12% |
| FPS | 1,124 | 1,252 | +11% |

Culled won **every round**. The earlier dip was noise.

### Change 3: build the HttpClient on a background thread

`HttpClient.newBuilder().build()` takes ~250 ms (the JDK initialises its TLS/SSL support), and it ran on
the render thread inside `create()`, before the first frame. `ApiClient` now builds it with
`CompletableFuture.supplyAsync(...)`, and every request chains on it with `thenCompose`. The first frame
doesn't wait, and the client is ready long before anyone clicks "Log in".

| (median) | Before | After (4 runs) |
|---|---|---|
| `create()` → first frame | 603 ms | **207 ms** (−66%) |
| `main()` → `create()` (LWJGL + window) | 758 ms | 725 ms (unchanged, not our code) |
| Startup total | 1,633 ms | **1,134 ms** (−31%) |

### Tried and rejected: AppCDS (class data sharing)

`-XX:+AutoCreateSharedArchive` should cut JVM class-loading time by mapping a saved archive of classes.
It **didn't work here:** the JVM refused to create the archive (`Error: non-empty directory
'...\build\classes\java\main'`), because CDS only archives classes from JAR files, and `gradlew lwjgl3:run`
runs from class folders. It would need the game packaged as a jar first. The runs with the flag were
also slower (no archive, plus the dump attempt), so it was reverted.

### Not applied (with reasons)

- **Texture atlas:** an atlas merges many images into one texture to avoid texture switches. The profiler shows **1 texture binding per frame** (the font), because everything else is drawn with `ShapeRenderer`. Nothing to merge.
- **Object pooling for `Vector2`:** the game loop already reuses its vectors (`Car` keeps `forward`/`lateral`, `RaceScreen` keeps `renderPosition`/`ghostPosition`/`cameraTarget`, `Track` keeps a scratch `hit` vector). After change 1 the render thread allocates **7 bytes per frame**, so there's nothing left to pool. The ghost samples (30 per second) are immutable records handed to the codec, so pooling them would add complexity for no measurable gain.

### Game summary

| Metric | Before | After |
|---|---|---|
| Garbage on the render thread | 2,808 B/frame | **7 B/frame** |
| GC collections per 20 s | 1 | **0** |
| Vertices / draw calls per frame | 6,714 / 3 | **~2,300 / 2** |
| `render()` avg | 0.429 ms | **0.362 ms** (median of 4 final runs) |
| `render()` p99 | 1.455 ms | 1.39 ms |
| Startup total (JVM → first frame) | 1,633 ms | **1,134 ms** |

At a normal 60 Hz with vsync, a frame has 16.7 ms; the game uses about 0.4 ms of CPU of that.

---

## 2. Backend load test

### How it was measured

- **Tool:** [k6](https://k6.io), script [`loadtest/ghostlap.js`](../loadtest/ghostlap.js). It runs in Docker on the compose network (`cd infra; docker compose --profile loadtest run --rm k6`), so no k6 install is needed, and it talks to the services directly, not through Docker Desktop's port forwarding.
- **Scenario:** 50 virtual players. Ramp up over 20 s, hold for 60 s, ramp down over 10 s.
  - Each player registers once (`POST /auth/register`).
  - Then it loops: submit a valid 16 s lap (`POST /laps`, ~4.4 KB of base64 ghost, so a real validation and a Postgres insert with `bytea`), read the leaderboard 3 times (`GET /tracks/1/leaderboard?limit=10`), and pause 0.1 s.
- **Thresholds** (pass/fail): <1% errors, p95 under 2 s for register, under 500 ms for submit lap, and under 200 ms for leaderboard.
- **Each run starts from an empty database** (`docker compose down -v`), so runs are comparable.
- **Diagnosis during the run:** Prometheus (Hikari pool usage, CPU, server-side time per endpoint, non-2xx responses) and Postgres's own counters (`pg_stat_user_tables`).
- **Caveat:** k6, both services, Postgres, Redis, Prometheus and Grafana all share one laptop. Whole-machine CPU reached about 90%, so these numbers say more about this laptop than about the services' real limits.

### First run

| | Requests/s | p95 | p99 | Errors |
|---|---|---|---|---|
| All requests | **506** | 155 ms | 248 ms | 4 / 45,630 (HTTP 500) |
| leaderboard | | 104 ms | 173 ms | |
| submit lap | | 217 ms | 330 ms | |
| register | | 2.48 s (threshold failed) | 3.24 s | |

### What the metrics showed

1. **A concurrency bug.** The 4 errors were HTTP 500s on the leaderboard (`ArrayIndexOutOfBoundsException`).
   - Saving a new best writes two Redis keys with two separate commands: the ranking (sorted set) and the details (hash).
   - A leaderboard read that lands between them sees a player with no details.
   - It only appears under concurrent load, which is why it never showed up in the tests.
2. **A hidden extra query per lap.** Postgres counters for `lap_ghosts`: **11,395 rows inserted and exactly 11,395 index lookups.**
   - `LapGhost`'s id is set by us (it's the lap's id), so Spring Data's `save()` couldn't tell it was new.
   - So it called `merge()`: a SELECT to check whether the row exists, then the INSERT.
3. **Register is slow by design.** BCrypt deliberately costs ~100 ms of CPU per password, and 50 players registered at once while the CPU was busy. Not a bug; lowering the BCrypt cost would weaken password storage.
4. **The DB connection pool was near its limit, but not the bottleneck:** 10/10 connections active at peak, with 1 waiting, but the average wait to get a connection was only 6 ms.

### Fixes

- `LapGhost implements Persistable<Long>`: `isNew()` is true until the entity is saved or loaded (`@PostPersist`/`@PostLoad`), so `save()` does a plain INSERT. A regression test counts the SQL statements (exactly 1).
- Both Redis writes (and the rebuild's two `RENAME`s) run in one **MULTI/EXEC transaction**, so other clients see both changes or neither. The reader also skips a row with missing details instead of failing the whole response. A regression test covers it.

### After: the noise trap, then a fair A/B comparison

The first run after the fix looked spectacular: **763 req/s, p95 88 ms** (+51%). But register, which I
didn't change, also got 4.5x faster (p95 2.48 s -> 545 ms), so the laptop was simply less busy than during
the first run. To measure only the code change, I built an image of the **old** race-service and alternated
old/new, each on a fresh database, 2 rounds:

| Run | Requests/s | p95 | p99 | submit lap p95 | leaderboard p95 | Errors |
|---|---|---|---|---|---|---|
| before, round 1 | 754.5 | 88.5 ms | 148.3 ms | 132.8 ms | 56.0 ms | 3 (HTTP 500) |
| **after, round 1** | **816.0** | **77.1 ms** | **130.0 ms** | **116.7 ms** | **51.9 ms** | **0** |
| before, round 2 | 750.1 | 86.3 ms | 132.6 ms | 123.0 ms | 53.2 ms | 0 |
| **after, round 2** | **848.3** | **68.1 ms** | **113.6 ms** | **104.6 ms** | **46.8 ms** | **0** |

| Average of both rounds | Before | After | Change |
|---|---|---|---|
| Requests/s | 752 | **832** | **+11%** |
| p95 (all requests) | 87.4 ms | **72.6 ms** | **-17%** |
| p99 (all requests) | 140.5 ms | **121.8 ms** | -13% |
| submit lap, avg / p95 | 54.7 / 127.9 ms | **44.7 / 110.7 ms** | -18% / -13% |
| HTTP 500s | 3 | **0** | bug fixed |
| `lap_ghosts` index lookups per insert | 1 | **0** | (17,166 inserts, 0 lookups) |

Removing one query per lap gave about **+11% throughput** under identical conditions. Every request got
faster, not only lap submission: less database work per lap frees CPU and connections for everyone.

### What I'd look at next
- **Run the load generator on a different machine** from the services, for numbers that reflect the services rather than the laptop.
- **Logging:** one access-log line per request goes to the console synchronously. At thousands of requests per second, async logging would help.
- **`ZADD ... LT`:** a single atomic "only lower the score" command would replace the read-then-write check when the same player submits two laps at the same moment.

