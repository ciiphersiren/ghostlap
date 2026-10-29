package com.ghostlap.race.lap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Every submitted lap, accepted or rejected (a row in {@code laps}). The ghost bytes live in a
 * separate table (LapGhost) so listing laps never loads the big binary column.
 */
@Entity
@Table(name = "laps")
public class Lap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** From the token. Players live in player-service's database, so there is no foreign key. */
    @Column(name = "player_id", nullable = false)
    private long playerId;

    /** Copied from the token, so the leaderboard can show names without calling player-service. */
    @Column(nullable = false, length = 20)
    private String username;

    @Column(name = "track_id", nullable = false)
    private int trackId;

    @Column(name = "lap_time_ms", nullable = false)
    private int lapTimeMs;

    /** Stored as a Postgres integer[] column. */
    @Column(name = "checkpoint_times_ms", nullable = false)
    private int[] checkpointTimesMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LapStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason", length = 30)
    private RejectReason rejectReason;

    /** Human-readable explanation of the rejection, for debugging. Not sent to the client. */
    @Column(name = "reject_detail", length = 300)
    private String rejectDetail;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    /** JPA needs a no-argument constructor. */
    protected Lap() {
    }

    public Lap(long playerId, String username, int trackId, int lapTimeMs, int[] checkpointTimesMs,
               RejectReason rejectReason, String rejectDetail) {
        this.playerId = playerId;
        this.username = username;
        this.trackId = trackId;
        this.lapTimeMs = lapTimeMs;
        this.checkpointTimesMs = checkpointTimesMs;
        this.status = rejectReason == null ? LapStatus.ACCEPTED : LapStatus.REJECTED;
        this.rejectReason = rejectReason;
        this.rejectDetail = rejectDetail;
        this.submittedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getPlayerId() {
        return playerId;
    }

    public String getUsername() {
        return username;
    }

    public int getTrackId() {
        return trackId;
    }

    public int getLapTimeMs() {
        return lapTimeMs;
    }

    public int[] getCheckpointTimesMs() {
        return checkpointTimesMs;
    }

    public LapStatus getStatus() {
        return status;
    }

    public RejectReason getRejectReason() {
        return rejectReason;
    }

    public String getRejectDetail() {
        return rejectDetail;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
