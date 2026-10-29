package com.ghostlap.race.leaderboard;

import com.ghostlap.race.lap.Lap;
import com.ghostlap.race.lap.LapRepository;
import com.ghostlap.race.track.Track;
import com.ghostlap.race.track.TrackCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Leaderboards in Redis. Per track there are two keys:
 * <ul>
 *   <li>{@code leaderboard:track:1} - a sorted set. Member = playerId, score = their best lap time in ms.
 *       Redis keeps it sorted, so "top 10" and "what's my rank" are fast (O(log n)).</li>
 *   <li>{@code leaderboard:track:1:laps} - a hash. Field = playerId, value = "lapId:username",
 *       the details shown for each row.</li>
 * </ul>
 * Postgres is the source of truth. If Redis loses its data, {@link #rebuild} recreates both keys.
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private final StringRedisTemplate redis;
    private final LapRepository laps;
    private final TrackCatalog tracks;

    public LeaderboardService(StringRedisTemplate redis, LapRepository laps, TrackCatalog tracks) {
        this.redis = redis;
        this.laps = laps;
        this.tracks = tracks;
    }

    static String boardKey(int trackId) {
        return "leaderboard:track:" + trackId;
    }

    static String lapsKey(int trackId) {
        return boardKey(trackId) + ":laps";
    }

    /** Adds an accepted lap, but only if it beats the player's current best on that track. */
    public void submit(Lap lap) {
        String player = String.valueOf(lap.getPlayerId());
        Double currentBest = redis.opsForZSet().score(boardKey(lap.getTrackId()), player);
        if (currentBest != null && currentBest <= lap.getLapTimeMs()) {
            return;
        }
        String details = lap.getId() + ":" + lap.getUsername();
        // Both writes in one Redis transaction (MULTI ... EXEC): other clients see both or neither.
        // Without it, the load test caught a reader between the two writes (a row with no details -> 500).
        inTransaction(ops -> {
            ops.opsForHash().put(lapsKey(lap.getTrackId()), player, details);
            ops.opsForZSet().add(boardKey(lap.getTrackId()), player, lap.getLapTimeMs());
        });
    }

    /**
     * Runs Redis commands as one MULTI/EXEC transaction: they are queued and then executed together,
     * with no other client's command in between.
     */
    private void inTransaction(Consumer<RedisOperations<String, String>> commands) {
        redis.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                ops.multi();
                commands.accept(ops);
                return ops.exec();
            }
        });
    }

    /** The player's position (1 = fastest), or null if they have no accepted lap on this track. */
    public Long rankOf(int trackId, long playerId) {
        Long zeroBasedRank = redis.opsForZSet().rank(boardKey(trackId), String.valueOf(playerId));
        return zeroBasedRank == null ? null : zeroBasedRank + 1;
    }

    /** The fastest {@code limit} players, best first. */
    public List<LeaderboardEntry> top(int trackId, int limit) {
        tracks.get(trackId); // 404 for unknown tracks
        // ZRANGE ... WITHSCORES: lowest scores (fastest times) first.
        Set<TypedTuple<String>> rows = redis.opsForZSet().rangeWithScores(boardKey(trackId), 0, limit - 1);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Object> players = new ArrayList<>();
        for (TypedTuple<String> row : rows) {
            players.add(row.getValue());
        }
        // HMGET: the details of all rows in one round trip.
        List<Object> details = redis.opsForHash().multiGet(lapsKey(trackId), players);

        List<LeaderboardEntry> entries = new ArrayList<>();
        int i = 0;
        for (TypedTuple<String> row : rows) {
            Object detail = details.get(i);
            i++;
            if (detail == null) {
                // Shouldn't happen (writes are transactional), but never fail the whole board for one row.
                log.warn("Leaderboard row for player {} has no details; skipped", row.getValue());
                continue;
            }
            String[] lapIdAndName = detail.toString().split(":", 2);
            entries.add(new LeaderboardEntry(i, lapIdAndName[1], row.getScore().intValue(), Long.parseLong(lapIdAndName[0])));
        }
        return entries;
    }

    /**
     * Recreates a track's leaderboard from Postgres (each player's best accepted lap). It is built
     * under temporary keys and then renamed into place, so readers never see a half-built board.
     *
     * @return the number of players on the rebuilt board
     */
    public int rebuild(int trackId) {
        List<Lap> bestLaps = laps.findBestAcceptedLapPerPlayer(trackId);
        String tmpBoard = boardKey(trackId) + ":rebuilding";
        String tmpLaps = lapsKey(trackId) + ":rebuilding";
        redis.delete(List.of(tmpBoard, tmpLaps));

        if (bestLaps.isEmpty()) {
            redis.delete(List.of(boardKey(trackId), lapsKey(trackId)));
            return 0;
        }
        for (Lap lap : bestLaps) {
            String player = String.valueOf(lap.getPlayerId());
            redis.opsForZSet().add(tmpBoard, player, lap.getLapTimeMs());
            redis.opsForHash().put(tmpLaps, player, lap.getId() + ":" + lap.getUsername());
        }
        // Swap both keys in one transaction, so a reader never sees a new board with old details.
        inTransaction(ops -> {
            ops.rename(tmpBoard, boardKey(trackId));
            ops.rename(tmpLaps, lapsKey(trackId));
        });
        return bestLaps.size();
    }

    /** On startup, rebuild any leaderboard that is missing from Redis (e.g. Redis was restarted empty). */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildMissingBoards() {
        for (Track track : tracks.all()) {
            if (!Boolean.TRUE.equals(redis.hasKey(boardKey(track.id())))) {
                int players = rebuild(track.id());
                log.info("Rebuilt leaderboard for track {} from Postgres: {} players", track.id(), players);
            }
        }
    }
}
