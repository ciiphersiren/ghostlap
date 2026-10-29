package com.ghostlap;

import com.badlogic.gdx.Game;
import com.ghostlap.race.RaceScreen;

/** Entry point shared by all platforms. For now it goes straight to the race (menus come later). */
public class GhostLapGame extends Game {

    @Override
    public void create() {
        setScreen(new RaceScreen());
    }

    @Override
    public void dispose() {
        if (getScreen() != null) {
            getScreen().dispose();
        }
    }
}
