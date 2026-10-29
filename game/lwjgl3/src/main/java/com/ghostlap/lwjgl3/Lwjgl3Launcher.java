package com.ghostlap.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.ghostlap.GhostLapGame;
import com.ghostlap.perf.StartupTimer;

/** Starts the desktop (LWJGL3) version of the game. */
public class Lwjgl3Launcher {

    public static void main(String[] args) {
        StartupTimer.mainStarted();
        // Server addresses come from env vars; the defaults match infra/docker-compose.yml.
        String playerServiceUrl = env("GHOSTLAP_PLAYER_URL", "http://localhost:8081");
        String raceServiceUrl = env("GHOSTLAP_RACE_URL", "http://localhost:8082");
        // GHOSTLAP_BENCHMARK=1: measure performance (see com.ghostlap.perf.Benchmark).
        boolean benchmark = "1".equals(System.getenv("GHOSTLAP_BENCHMARK"));
        new Lwjgl3Application(new GhostLapGame(playerServiceUrl, raceServiceUrl, benchmark), createConfiguration(benchmark));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Lwjgl3ApplicationConfiguration createConfiguration(boolean benchmark) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("GhostLap");
        config.setWindowedMode(1280, 720);
        if (benchmark) {
            // No frame cap: frames run as fast as possible, so frame time shows our real cost.
            config.useVsync(false);
            config.setForegroundFPS(0);
        } else {
            // Vsync caps rendering at the monitor's refresh rate. Physics still runs at exactly 60 Hz.
            config.useVsync(true);
            config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        }
        return config;
    }
}
