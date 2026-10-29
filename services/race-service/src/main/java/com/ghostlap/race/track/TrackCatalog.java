package com.ghostlap.race.track;

import com.ghostlap.race.error.NotFoundException;
import com.ghostlap.shared.GameConstants;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/** The tracks the server accepts laps for. GhostLap has one. */
@Component
public class TrackCatalog {

    /*
     * Track 1 minimum lap time: the centerline is 9118 units long, and at MAX_SPEED (600 units/s)
     * that takes 15.2 s. The game's autopilot does 15.33 s. A perfect racing line cuts corners inside
     * the 150-unit-wide track, so we allow roughly 15% below the centerline time.
     */
    private static final Track TRACK_1 = new Track(GameConstants.TRACK_ID, "GhostLap Circuit", 13_000);

    private final Map<Integer, Track> tracks = Map.of(TRACK_1.id(), TRACK_1);

    public Track get(int trackId) {
        Track track = tracks.get(trackId);
        if (track == null) {
            throw new NotFoundException("Track " + trackId + " not found");
        }
        return track;
    }

    public Collection<Track> all() {
        return tracks.values();
    }
}
