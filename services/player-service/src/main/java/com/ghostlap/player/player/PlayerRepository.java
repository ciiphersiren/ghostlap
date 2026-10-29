package com.ghostlap.player.player;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data writes the SQL for these from the method names. */
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
