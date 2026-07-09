package com.yowyob.tiibntick.core.auth.domain.exception;

/**
 * Root domain exception for authentication and security context errors in tnt-auth-core.
 * Never wraps Spring Security exceptions directly — maintains the hexagonal boundary.
 *
 * @author MANFOUO Braun
 */
public class TntAuthException extends RuntimeException {

    private final String code;

    public TntAuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public TntAuthException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static TntAuthException unauthorized(String message) {
        return new TntAuthException("AUTH_UNAUTHORIZED", message);
    }

    public static TntAuthException forbidden(String resource, String action) {
        return new TntAuthException(
                "AUTH_FORBIDDEN",
                "Access denied: permission '" + resource + ":" + action + "' is required."
        );
    }

    public static TntAuthException tokenExpired() {
        return new TntAuthException("AUTH_TOKEN_EXPIRED", "The provided token has expired.");
    }

    public static TntAuthException tokenInvalid(String detail) {
        return new TntAuthException("AUTH_TOKEN_INVALID", "Invalid token: " + detail);
    }

    public static TntAuthException missingContext() {
        return new TntAuthException(
                "AUTH_MISSING_CONTEXT",
                "No authenticated security context found in the reactive chain."
        );
    }

    public static TntAuthException actorNotLinked(String userId) {
        return new TntAuthException(
                "AUTH_ACTOR_NOT_LINKED",
                "No TiiBnTick actor profile is linked to userId=" + userId
        );
    }
}
