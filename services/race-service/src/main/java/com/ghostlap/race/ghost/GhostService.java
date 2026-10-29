package com.ghostlap.race.ghost;

import com.ghostlap.race.error.NotFoundException;
import com.ghostlap.race.lap.Lap;
import com.ghostlap.race.lap.LapGhost;
import com.ghostlap.race.lap.LapGhostRepository;
import com.ghostlap.race.lap.LapRepository;
import com.ghostlap.race.lap.LapStatus;
import com.ghostlap.race.track.TrackCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

/** Ghosts are only served for accepted laps. */
@Service
@Transactional(readOnly = true)
public class GhostService {

    private final TrackCatalog tracks;
    private final LapRepository laps;
    private final LapGhostRepository ghosts;

    public GhostService(TrackCatalog tracks, LapRepository laps, LapGhostRepository ghosts) {
        this.tracks = tracks;
        this.laps = laps;
        this.ghosts = ghosts;
    }

    /** The fastest accepted lap on the track. Read from Postgres, the source of truth. */
    public GhostResponse topGhost(int trackId) {
        tracks.get(trackId); // 404 for unknown tracks
        Lap lap = laps.findFirstByTrackIdAndStatusOrderByLapTimeMsAscIdAsc(trackId, LapStatus.ACCEPTED)
                .orElseThrow(() -> new NotFoundException("No accepted laps on track " + trackId + " yet"));
        return toResponse(lap);
    }

    public GhostResponse ghostOfLap(long lapId) {
        Lap lap = laps.findById(lapId)
                .filter(l -> l.getStatus() == LapStatus.ACCEPTED)
                .orElseThrow(() -> new NotFoundException("No ghost for lap " + lapId));
        return toResponse(lap);
    }

    private GhostResponse toResponse(Lap lap) {
        LapGhost ghost = ghosts.findById(lap.getId())
                .orElseThrow(() -> new NotFoundException("No ghost for lap " + lap.getId()));
        return new GhostResponse(lap.getId(), lap.getUsername(), lap.getTrackId(), lap.getLapTimeMs(),
                lap.getSubmittedAt(), Base64.getEncoder().encodeToString(ghost.getData()));
    }
}
