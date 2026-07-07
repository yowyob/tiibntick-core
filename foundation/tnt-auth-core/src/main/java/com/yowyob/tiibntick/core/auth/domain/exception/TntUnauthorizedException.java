package com.yowyob.tiibntick.core.auth.domain.exception;

/**
 * Thrown when an operation is attempted without a valid authenticated context.
 * Mapped to HTTP 401 at the adapter layer.
 *
 * @author MANFOUO Braun
 */
public class TntUnauthorizedException extends TntAuthException {

    public TntUnauthorizedException() {
        super("AUTH_UNAUTHORIZED", "Authentication is required to access this resource.");
    }

    public TntUnauthorizedException(String message) {
        super("AUTH_UNAUTHORIZED", message);
    }
}
