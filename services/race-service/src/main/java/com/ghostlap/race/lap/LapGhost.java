package com.ghostlap.race.lap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * The ghost of an accepted lap: the exact GhostCodec bytes the client sent (gzipped GHST format).
 * Same id as its lap.
 *
 * Implements Persistable so Spring Data knows a new LapGhost is new. Its id is set by us (not
 * generated), and by default Spring Data treats "id already set" as "maybe exists", so save() did
 * merge(): a SELECT before every INSERT. The load test showed one extra query per lap
 * (docs/PERFORMANCE.md).
 */
@Entity
@Table(name = "lap_ghosts")
public class LapGhost implements Persistable<Long> {

    @Id
    @Column(name = "lap_id")
    private Long lapId;

    /**
     * A plain byte[] maps to a Postgres {@code bytea} column. (Adding @Lob would make Hibernate use a
     * Postgres "large object" stored outside the table instead, which is harder to manage.)
     */
    @Column(nullable = false)
    private byte[] data;

    /** True until the row is saved or loaded. Not a column (@Transient). */
    @Transient
    private boolean isNew = true;

    /** JPA needs a no-argument constructor. */
    protected LapGhost() {
    }

    public LapGhost(long lapId, byte[] data) {
        this.lapId = lapId;
        this.data = data;
    }

    @Override
    public Long getId() {
        return lapId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /** JPA calls this after an INSERT or after loading the row: from then on it's not new. */
    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    public Long getLapId() {
        return lapId;
    }

    public byte[] getData() {
        return data;
    }
}
