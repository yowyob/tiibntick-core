package com.yowyob.tiibntick.core.delivery.domain.model.enums;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Operational (vetting) status of a delivery person within the platform.
 *
 * <p>Allowed transitions:
 * <pre>
 *   PENDING   → APPROVED, REJECTED
 *   APPROVED  → SUSPENDED, REVOKED
 *   SUSPENDED → APPROVED, REVOKED
 *   REJECTED  → (terminal)
 *   REVOKED   → (terminal)
 * </pre>
 * A transition to the current state is always allowed (idempotent).
 *
 * @author MANFOUO Braun
 */
public enum DeliveryPersonStatus {

    PENDING,
    APPROVED,
    SUSPENDED,
    REJECTED,
    /** Approval withdrawn for good (terminal), unlike SUSPENDED which can be lifted. */
    REVOKED;

    private static final Map<DeliveryPersonStatus, Set<DeliveryPersonStatus>> ALLOWED =
            new EnumMap<>(DeliveryPersonStatus.class);

    static {
        ALLOWED.put(PENDING, Set.of(APPROVED, REJECTED));
        ALLOWED.put(APPROVED, Set.of(SUSPENDED, REVOKED));
        ALLOWED.put(SUSPENDED, Set.of(APPROVED, REVOKED));
        ALLOWED.put(REJECTED, Set.of());
        ALLOWED.put(REVOKED, Set.of());
    }

    /** True when moving from this status to {@code target} is permitted (same status included). */
    public boolean canTransitionTo(DeliveryPersonStatus target) {
        return this == target || ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** True for the statuses from which no transition is possible. */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
