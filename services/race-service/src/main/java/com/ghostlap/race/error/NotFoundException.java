package com.ghostlap.race.error;

/** Unknown track, lap or ghost. Mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
