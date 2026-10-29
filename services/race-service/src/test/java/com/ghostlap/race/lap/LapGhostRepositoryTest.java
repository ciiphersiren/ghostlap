package com.ghostlap.race.lap;

import com.ghostlap.race.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression test for the load-test finding: saving a ghost used to SELECT before the INSERT. */
class LapGhostRepositoryTest extends IntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void savingANewGhostIsASingleInsert() {
        Lap lap = laps.save(new Lap(1, "reet", 1, 16_000, new int[]{2000, 16_000}, null, null));
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        ghosts.save(new LapGhost(lap.getId(), new byte[]{1, 2, 3}));

        assertThat(stats.getPrepareStatementCount()).isEqualTo(1); // the INSERT, no SELECT first
        assertThat(ghosts.findById(lap.getId())).isPresent();
    }

    @Test
    void loadedGhostIsNotNew() {
        Lap lap = laps.save(new Lap(1, "reet", 1, 16_000, new int[]{2000, 16_000}, null, null));
        ghosts.save(new LapGhost(lap.getId(), new byte[]{1, 2, 3}));

        assertThat(ghosts.findById(lap.getId()).orElseThrow().isNew()).isFalse();
    }
}
