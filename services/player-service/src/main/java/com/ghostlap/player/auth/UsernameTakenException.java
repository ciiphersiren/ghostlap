package com.ghostlap.player.auth;

/** Mapped to 409 Conflict. */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("Username '" + username + "' is already taken");
    }
}
