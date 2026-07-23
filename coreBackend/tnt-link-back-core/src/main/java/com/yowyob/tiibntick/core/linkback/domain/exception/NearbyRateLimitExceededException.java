package com.yowyob.tiibntick.core.linkback.domain.exception;

/**
 * Raised by {@link com.yowyob.tiibntick.core.linkback.adapter.in.web.ratelimit.NearbyRateLimiter}
 * when a caller exceeds the per-user throttle on a {@code /nearby} endpoint.
 * Mapped to HTTP 429 by {@code LinkBackExceptionHandler}.
 *
 * <p>Phase 0 stop-gap — see docs/audits/remediation/phase-0-critical.md, Chantier G.
 */
public class NearbyRateLimitExceededException extends RuntimeException {

    public NearbyRateLimitExceededException(String message) {
        super(message);
    }
}
