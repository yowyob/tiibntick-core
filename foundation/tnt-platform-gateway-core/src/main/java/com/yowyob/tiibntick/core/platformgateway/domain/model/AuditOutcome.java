package com.yowyob.tiibntick.core.platformgateway.domain.model;

/**
 * Outcome of a single platform-gateway request, recorded in {@link ClientAuditLog}.
 *
 * @author MANFOUO Braun
 */
public enum AuditOutcome {
    SUCCESS,
    INVALID_KEY,
    UNKNOWN_CLIENT,
    SUSPENDED,
    EXPIRED,
    FORBIDDEN_SCOPE
}
