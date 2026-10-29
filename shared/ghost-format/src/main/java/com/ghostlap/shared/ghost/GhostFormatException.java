package com.ghostlap.shared.ghost;

/** Thrown when ghost bytes are corrupt, truncated or not a ghost at all. The server maps it to BAD_GHOST. */
public class GhostFormatException extends RuntimeException {

    public GhostFormatException(String message) {
        super(message);
    }

    public GhostFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
