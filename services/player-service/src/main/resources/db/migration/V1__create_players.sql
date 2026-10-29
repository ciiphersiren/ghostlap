-- Flyway runs each V<n>__*.sql file once, in order, and records it in flyway_schema_history.
CREATE TABLE players (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

-- "Reet" and "reet" count as the same name.
CREATE UNIQUE INDEX players_username_lower_idx ON players (LOWER(username));
