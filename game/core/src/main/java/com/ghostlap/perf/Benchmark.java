package com.ghostlap.perf;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.profiling.GLProfiler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/**
 * Benchmark mode (env var GHOSTLAP_BENCHMARK=1): the car drives itself with vsync off, so frames
 * run as fast as the machine allows. After a warm-up (so the JIT compiler has optimised the hot
 * code) it measures for a fixed time, prints a report and closes the game.
 *
 * Measured per frame:
 * <ul>
 *   <li>frame time: from the start of one frame to the start of the next (what FPS is based on)</li>
 *   <li>render() CPU time: just our update + draw code, without waiting for the GPU/driver</li>
 *   <li>bytes allocated on the render thread (garbage the GC must clean up later)</li>
 *   <li>OpenGL draw calls and vertices (from libGDX's GLProfiler)</li>
 * </ul>
 * The measuring itself doesn't allocate: times go into preallocated arrays.
 */
public class Benchmark {

    private static final float WARMUP_SECONDS = 5f;
    private static final float MEASURE_SECONDS = 20f;
    private static final int MAX_FRAMES = 400_000;

    private final GLProfiler profiler = new GLProfiler(Gdx.graphics);
    private final com.sun.management.ThreadMXBean threads =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private final long[] frameNanos = new long[MAX_FRAMES];
    private final long[] renderNanos = new long[MAX_FRAMES];
    private int frames;

    private final long startNanos = System.nanoTime();
    private long measureStartNanos;
    private boolean measuring;
    private long frameStartNanos;
    private long previousFrameStartNanos;

    private long allocatedAtStart;
    private long gcCountAtStart;
    private long gcTimeAtStart;
    private long drawCalls;
    private double vertices;
    private long textureBindings;

    public Benchmark() {
        profiler.enable();
    }

    /** Call at the very start of each frame. */
    public void frameStarted() {
        previousFrameStartNanos = frameStartNanos;
        frameStartNanos = System.nanoTime();
    }

    /** Call at the very end of each frame. */
    public void frameEnded() {
        long now = System.nanoTime();
        if (!measuring) {
            if ((now - startNanos) / 1e9 >= WARMUP_SECONDS) {
                startMeasuring(now);
            }
            profiler.reset();
            return;
        }
        if (previousFrameStartNanos != 0 && frames < MAX_FRAMES) {
            frameNanos[frames] = frameStartNanos - previousFrameStartNanos;
            renderNanos[frames] = now - frameStartNanos;
            frames++;
        }
        drawCalls += profiler.getDrawCalls();
        vertices += profiler.getVertexCount().total;
        textureBindings += profiler.getTextureBindings();
        profiler.reset();

        if ((now - measureStartNanos) / 1e9 >= MEASURE_SECONDS) {
            report(now);
            Gdx.app.exit();
        }
    }

    private void startMeasuring(long now) {
        measuring = true;
        measureStartNanos = now;
        previousFrameStartNanos = 0; // the warm-up's last frame doesn't count
        allocatedAtStart = threads.getCurrentThreadAllocatedBytes();
        gcCountAtStart = gcCount();
        gcTimeAtStart = gcTimeMs();
    }

    private void report(long now) {
        double seconds = (now - measureStartNanos) / 1e9;
        long allocated = threads.getCurrentThreadAllocatedBytes() - allocatedAtStart;
        long[] frame = Arrays.copyOf(frameNanos, frames);
        long[] render = Arrays.copyOf(renderNanos, frames);
        Arrays.sort(frame);
        Arrays.sort(render);

        log("%.1f s measured, %d frames, %.0f FPS", seconds, frames, frames / seconds);
        log("frame time ms:  avg %.3f | p50 %.3f | p95 %.3f | p99 %.3f | max %.3f",
                avgMs(frame), pctMs(frame, 50), pctMs(frame, 95), pctMs(frame, 99), ms(frame[frames - 1]));
        log("render() ms:    avg %.3f | p50 %.3f | p95 %.3f | p99 %.3f | max %.3f",
                avgMs(render), pctMs(render, 50), pctMs(render, 95), pctMs(render, 99), ms(render[frames - 1]));
        log("allocated on render thread: %.1f MB total, %.0f bytes/frame, %.2f MB/s",
                allocated / 1e6, (double) allocated / frames, allocated / 1e6 / seconds);
        log("GC: %d collections, %d ms total", gcCount() - gcCountAtStart, gcTimeMs() - gcTimeAtStart);
        log("per frame: %.1f draw calls, %.0f vertices, %.1f texture bindings",
                (double) drawCalls / frames, vertices / frames, (double) textureBindings / frames);
    }

    private static void log(String format, Object... args) {
        Gdx.app.log("Benchmark", String.format(format, args));
    }

    private double avgMs(long[] values) {
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return ms(sum) / values.length;
    }

    /** Nearest-rank percentile of a sorted array. */
    private static double pctMs(long[] sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return ms(sorted[Math.max(0, index)]);
    }

    private static double ms(long nanos) {
        return nanos / 1e6;
    }

    private static long gcCount() {
        long count = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, gc.getCollectionCount());
        }
        return count;
    }

    private static long gcTimeMs() {
        long time = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            time += Math.max(0, gc.getCollectionTime());
        }
        return time;
    }
}
