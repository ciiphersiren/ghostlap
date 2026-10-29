package com.ghostlap.net;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Why a server call failed, sorted into the cases the game handles differently.
 *
 * @param status  the HTTP status, or 0 if no response arrived
 * @param message short text to show the player
 */
public record ApiError(Kind kind, int status, String message) {

    public enum Kind {
        /** No response: server down, wrong address, or timed out. */
        UNREACHABLE,
        /** 401: wrong username/password at login, or an expired/invalid token. */
        UNAUTHORIZED,
        /** 404: e.g. no ghost on the server yet. */
        NOT_FOUND,
        /** 409: username already taken. */
        CONFLICT,
        /** 400: the server didn't accept the request's fields. */
        BAD_REQUEST,
        /** 5xx, or a response we couldn't understand. */
        SERVER_ERROR
    }

    public static ApiError unreachable() {
        return new ApiError(Kind.UNREACHABLE, 0, "Server unreachable");
    }

    public static ApiError badResponse(int status) {
        return new ApiError(Kind.SERVER_ERROR, status, "Unexpected response from the server");
    }

    /** Builds an error from an HTTP error response. Our services send ProblemDetail JSON bodies. */
    public static ApiError fromResponse(int status, String body) {
        Kind kind = switch (status) {
            case 400 -> Kind.BAD_REQUEST;
            case 401 -> Kind.UNAUTHORIZED;
            case 404 -> Kind.NOT_FOUND;
            case 409 -> Kind.CONFLICT;
            default -> Kind.SERVER_ERROR;
        };
        return new ApiError(kind, status, messageFromProblem(status, body));
    }

    /**
     * Uses the ProblemDetail's "detail" text. For validation errors (400) it shows the first field
     * error instead, e.g. "password: size must be between 8 and 72".
     */
    private static String messageFromProblem(int status, String body) {
        try {
            JsonValue problem = new JsonReader().parse(body);
            JsonValue errors = problem.get("errors");
            if (errors != null && errors.child != null) {
                return errors.child.name + ": " + errors.child.asString();
            }
            String detail = problem.getString("detail", null);
            if (detail != null) {
                return detail;
            }
        } catch (RuntimeException e) {
            // Not JSON (e.g. a proxy error page). Fall through to the generic message.
        }
        return "Server error (HTTP " + status + ")";
    }
}
