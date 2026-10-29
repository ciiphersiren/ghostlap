package com.ghostlap.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.ghostlap.GhostLapGame;

/** Starts the desktop (LWJGL3) version of the game. */
public class Lwjgl3Launcher {

    public static void main(String[] args) {
        new Lwjgl3Application(new GhostLapGame(), createConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration createConfiguration() {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("GhostLap");
        config.setWindowedMode(1280, 720);
        // Vsync caps rendering at the monitor's refresh rate. Physics still runs at exactly 60 Hz.
        config.useVsync(true);
        config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        return config;
    }
}
