package com.yowyob.tiibntick.core.geo.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Approval of a relay hub by an administrator (maintainer decision D-138, 2026-11-26), separate from its operating
 * status ({@link HubStatus}). It is authoritative for the Go-Freelancer relay points, which copy it. Agency has no
 * relay point (D-143): its hubs are managed by the Agency platform, outside this status.
 *
 * <p>Same machine as the couriers' ({@code DeliveryPersonStatus}):
 * <pre>
 *   PENDING   → APPROVED, REJECTED
 *   APPROVED  → SUSPENDED, REVOKED
 *   SUSPENDED → APPROVED, REVOKED
 *   REJECTED  → (final)
 *   REVOKED   → (final, D-129: definitive)
 * </pre>
 * Only an APPROVED hub is offered as a destination and accepts new deposits (D-140).
 */
public enum HubApprovalStatus {
    PENDING,
    APPROVED,
    SUSPENDED,
    REJECTED,
    REVOKED;

    private static final Map<HubApprovalStatus, Set<HubApprovalStatus>> ALLOWED = new EnumMap<>(HubApprovalStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(APPROVED, REJECTED));
        ALLOWED.put(APPROVED, EnumSet.of(SUSPENDED, REVOKED));
        ALLOWED.put(SUSPENDED, EnumSet.of(APPROVED, REVOKED));
        ALLOWED.put(REJECTED, EnumSet.noneOf(HubApprovalStatus.class));
        ALLOWED.put(REVOKED, EnumSet.noneOf(HubApprovalStatus.class));
    }

    /** True when the machine allows {@code this → target} (a status never transitions to itself). */
    public boolean canTransitionTo(HubApprovalStatus target) {
        return target != null && ALLOWED.get(this).contains(target);
    }

    /** True for the statuses no transition leaves. */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** D-140: only an approved hub is proposed as a destination and takes new deposits. */
    public boolean acceptsDeposits() {
        return this == APPROVED;
    }
}
