package com.ghostlap.player.player;

/** The token is valid but its player no longer exists. Mapped to 404. */
public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(long playerId) {
        super("Player " + playerId + " not found");
    }
}
