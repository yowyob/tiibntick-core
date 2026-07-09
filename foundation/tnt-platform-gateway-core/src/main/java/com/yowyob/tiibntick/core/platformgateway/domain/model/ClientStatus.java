package com.yowyob.tiibntick.core.platformgateway.domain.model;

/**
 * Lifecycle status of a {@link PlatformClient}.
 *
 * @author MANFOUO Braun
 */
public enum ClientStatus {
    ACTIVE,
    SUSPENDED,
    /** Terminal — soft-deleted, never physically removed for audit integrity. */
    DECOMMISSIONED
}
