package com.ghostlap.race.lap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The ghost of an accepted lap: the exact GhostCodec bytes the client sent (gzipped GHST format).
 * Same id as its lap.
 */
@Entity
@Table(name = "lap_ghosts")
public class LapGhost {

    @Id
    @Column(name = "lap_id")
    private Long lapId;

    /**
     * A plain byte[] maps to a Postgres {@code bytea} column. (Adding @Lob would make Hibernate use a
     * Postgres "large object" stored outside the table instead, which is harder to manage.)
     */
    @Column(nullable = false)
    private byte[] data;

    /** JPA needs a no-argument constructor. */
    protected LapGhost() {
    }

    public LapGhost(long lapId, byte[] data) {
        this.lapId = lapId;
        this.data = data;
    }

    public Long getLapId() {
        return lapId;
    }

    public byte[] getData() {
        return data;
    }
}
