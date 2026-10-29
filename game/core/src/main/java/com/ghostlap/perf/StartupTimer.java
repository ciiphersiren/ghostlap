package com.ghostlap.perf;

import com.badlogic.gdx.Gdx;

import java.lang.management.ManagementFactory;

/**
 * Measures startup in three phases and logs them once, when the first frame has been drawn:
 * <ol>
 *   <li>JVM start -> main(): the JVM itself starting and loading our first classes</li>
 *   <li>main() -> create(): LWJGL natives, the window and the OpenGL context</li>
 *   <li>create() -> first frame: our own loading (skin, fonts, track, first screen) and drawing it</li>
 * </ol>
 */
public final class StartupTimer {

    private static long jvmToMainMs = -1;
    private static long mainNanos;
    private static long createNanos;
    private static boolean reported;

    private StartupTimer() {
    }

    /** Call first thing in main(). */
    public static void mainStarted() {
        mainNanos = System.nanoTime();
        jvmToMainMs = ManagementFactory.getRuntimeMXBean().getUptime(); // ms since the JVM started
    }

    /** Call first thing in ApplicationListener.create(). */
    public static void createStarted() {
        createNanos = System.nanoTime();
    }

    /** Call after every frame; only the first call logs. */
    public static void frameRendered() {
        if (reported || jvmToMainMs < 0) {
            return;
        }
        reported = true;
        long now = System.nanoTime();
        long mainToCreateMs = (createNanos - mainNanos) / 1_000_000;
        long createToFrameMs = (now - createNanos) / 1_000_000;
        long totalMs = jvmToMainMs + (now - mainNanos) / 1_000_000;
        Gdx.app.log("Startup", String.format(
                "JVM start -> main(): %d ms | main() -> create(): %d ms | create() -> first frame: %d ms | total: %d ms",
                jvmToMainMs, mainToCreateMs, createToFrameMs, totalMs));
    }
}
