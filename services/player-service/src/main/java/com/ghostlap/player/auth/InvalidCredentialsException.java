package com.ghostlap.player.auth;

/**
 * Mapped to 401. Same message for "no such user" and "wrong password", so the login endpoint
 * doesn't tell an attacker which usernames exist.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
