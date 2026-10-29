-- Flyway runs each V<n>__*.sql file once, in order, and records it in flyway_schema_history.

-- Every submitted lap, accepted or rejected.
-- player_id has no foreign key: players live in player-service's own database.
CREATE TABLE laps (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id           BIGINT       NOT NULL,
    username            VARCHAR(20)  NOT NULL,
    track_id            INT          NOT NULL,
    lap_time_ms         INT          NOT NULL,
    checkpoint_times_ms INT[]        NOT NULL,
    status              VARCHAR(10)  NOT NULL CHECK (status IN ('ACCEPTED', 'REJECTED')),
    reject_reason       VARCHAR(30),
    reject_detail       VARCHAR(300),
    submitted_at        TIMESTAMPTZ  NOT NULL
);

-- Fastest accepted laps per track (top ghost, leaderboard rebuild). Partial index: only accepted rows.
CREATE INDEX laps_track_time_accepted_idx ON laps (track_id, lap_time_ms) WHERE status = 'ACCEPTED';
CREATE INDEX laps_player_idx ON laps (player_id);

-- Ghost files of accepted laps, kept apart from laps so lap queries don't drag the bytes along.
CREATE TABLE lap_ghosts (
    lap_id BIGINT PRIMARY KEY REFERENCES laps (id) ON DELETE CASCADE,
    data   BYTEA  NOT NULL
);
