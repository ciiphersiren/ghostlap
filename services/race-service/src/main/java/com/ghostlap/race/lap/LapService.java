package com.ghostlap.race.lap;

import com.ghostlap.race.leaderboard.LeaderboardService;
import com.ghostlap.race.track.Track;
import com.ghostlap.race.track.TrackCatalog;
import com.ghostlap.race.validation.LapValidator;
import com.ghostlap.race.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LapService {

    private static final Logger log = LoggerFactory.getLogger(LapService.class);

    private final TrackCatalog tracks;
    private final LapValidator validator;
    private final LapRepository laps;
    private final LapGhostRepository ghosts;
    private final LeaderboardService leaderboard;
    private final TransactionTemplate transaction;

    public LapService(TrackCatalog tracks, LapValidator validator, LapRepository laps, LapGhostRepository ghosts,
                      LeaderboardService leaderboard, TransactionTemplate transaction) {
        this.tracks = tracks;
        this.validator = validator;
        this.laps = laps;
        this.ghosts = ghosts;
        this.leaderboard = leaderboard;
        this.transaction = transaction;
    }

    /**
     * Validates and stores a lap. Order matters:
     * 1. Save to Postgres (lap + ghost in one transaction). This is the source of truth.
     * 2. Only after the commit, update the Redis leaderboard. If Redis is down, the lap is still
     *    saved and a rebuild will pick it up later.
     */
    public LapSubmissionResponse submit(long playerId, String username, LapSubmissionRequest request) {
        Track track = tracks.get(request.trackId());
        ValidationResult result = validator.validate(request, track);
        int[] checkpointTimes = request.checkpointTimesMs().stream().mapToInt(Integer::intValue).toArray();

        Lap lap = transaction.execute(status -> {
            Lap saved = laps.save(new Lap(playerId, username, request.trackId(), request.lapTimeMs(),
                    checkpointTimes, result.reason(), result.detail()));
            if (result.isAccepted()) {
                ghosts.save(new LapGhost(saved.getId(), result.ghostBytes()));
            }
            return saved;
        });

        if (!result.isAccepted()) {
            log.info("Lap {} by player {} rejected: {} ({})", lap.getId(), playerId, result.reason(), result.detail());
            return LapSubmissionResponse.rejected(lap.getId(), result.reason());
        }
        Long rank = null;
        try {
            leaderboard.submit(lap);
            rank = leaderboard.rankOf(lap.getTrackId(), playerId);
        } catch (DataAccessException e) {
            log.warn("Lap {} saved, but the Redis leaderboard update failed; it will appear after a rebuild", lap.getId(), e);
        }
        return LapSubmissionResponse.accepted(lap.getId(), rank);
    }
}
