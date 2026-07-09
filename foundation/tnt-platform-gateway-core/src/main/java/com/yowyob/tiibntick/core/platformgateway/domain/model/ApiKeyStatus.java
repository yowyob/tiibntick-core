package com.yowyob.tiibntick.core.platformgateway.domain.model;

/**
 * Lifecycle status of an {@link ApiKey}.
 *
 * @author MANFOUO Braun
 */
public enum ApiKeyStatus {
    ACTIVE,
    /** Still valid during a rotation grace window, but a replacement key already exists. */
    ROTATING,
    REVOKED,
    EXPIRED
}
