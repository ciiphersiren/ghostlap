package com.ghostlap.race.lap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LapRepository extends JpaRepository<Lap, Long> {

    /** The fastest lap with this status on a track (earliest wins a tie). Spring Data builds the SQL from the name. */
    Optional<Lap> findFirstByTrackIdAndStatusOrderByLapTimeMsAscIdAsc(int trackId, LapStatus status);

    /**
     * Each player's best accepted lap on a track. Used to rebuild the Redis leaderboard.
     * DISTINCT ON (player_id) is Postgres SQL: keep the first row per player in the ORDER BY order.
     */
    @Query(value = """
            SELECT DISTINCT ON (player_id) *
            FROM laps
            WHERE track_id = :trackId AND status = 'ACCEPTED'
            ORDER BY player_id, lap_time_ms, id
            """, nativeQuery = true)
    List<Lap> findBestAcceptedLapPerPlayer(int trackId);
}
