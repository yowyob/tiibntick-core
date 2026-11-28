package com.yowyob.tiibntick.core.geo.domain.model;

/**
 * Operating status of a relay hub (open, full, closed). The administrator's approval, revocation included, is a
 * separate status: {@link HubApprovalStatus} (maintainer decision D-138, 2026-11-26).
 *
 * Author: MANFOUO Braun
 */
public enum HubStatus {
    ACTIVE,
    FULL,
    TEMPORARILY_CLOSED,
    PERMANENTLY_CLOSED
}
