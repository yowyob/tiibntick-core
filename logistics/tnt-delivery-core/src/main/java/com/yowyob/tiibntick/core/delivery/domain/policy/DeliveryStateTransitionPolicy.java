package com.yowyob.tiibntick.core.delivery.domain.policy;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Encodes the legal state transitions for the delivery lifecycle.
 *
 * <p> — Added {@code PAUSED_BY_INCIDENT} transitions for tnt-incident-core integration:
 * <ul>
 *   <li>Any active status → {@code PAUSED_BY_INCIDENT} (incident blocks the delivery)</li>
 *   <li>{@code PAUSED_BY_INCIDENT} → {@code IN_TRANSIT} or {@code PICKED_UP}
 *       (incident resolved, delivery resumes from saved previous status)</li>
 * </ul>
 *
 * <p>Transition table:
 * <pre>
 * CREATED          → PICKED_UP, CANCELLED, PAUSED_BY_INCIDENT
 * PICKED_UP        → IN_TRANSIT, FAILED, CANCELLED, PAUSED_BY_INCIDENT
 * IN_TRANSIT       → DELIVERED, FAILED, AT_RELAY_POINT, PAUSED_BY_INCIDENT
 * AT_RELAY_POINT   → IN_TRANSIT, FAILED, PAUSED_BY_INCIDENT
 * PAUSED_BY_INCIDENT → IN_TRANSIT, PICKED_UP, CANCELLED (incident resolved / driver swapped)
 * DELIVERED        → (terminal)
 * FAILED           → IN_TRANSIT  (retry)
 * CANCELLED        → (terminal)
 * TIMED_OUT        → PAUSED_BY_INCIDENT, FAILED, CANCELLED (incident auto-created)
 * SLA_BREACHED     → IN_TRANSIT, PAUSED_BY_INCIDENT (warning state)
 * </pre>
 *
 * @author MANFOUO Braun
 */
public final class DeliveryStateTransitionPolicy {

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED_TRANSITIONS;

    static {
        ALLOWED_TRANSITIONS = new EnumMap<>(DeliveryStatus.class);

        ALLOWED_TRANSITIONS.put(DeliveryStatus.CREATED,
                EnumSet.of(DeliveryStatus.PICKED_UP, DeliveryStatus.CANCELLED,
                           DeliveryStatus.PAUSED_BY_INCIDENT));

        ALLOWED_TRANSITIONS.put(DeliveryStatus.PICKED_UP,
                EnumSet.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.FAILED,
                           DeliveryStatus.CANCELLED, DeliveryStatus.PAUSED_BY_INCIDENT));

        ALLOWED_TRANSITIONS.put(DeliveryStatus.IN_TRANSIT,
                EnumSet.of(DeliveryStatus.DELIVERED, DeliveryStatus.FAILED,
                           DeliveryStatus.AT_RELAY_POINT, DeliveryStatus.PAUSED_BY_INCIDENT));

        ALLOWED_TRANSITIONS.put(DeliveryStatus.AT_RELAY_POINT,
                EnumSet.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.FAILED,
                           DeliveryStatus.PAUSED_BY_INCIDENT));

        // From PAUSED_BY_INCIDENT — incident resolved: resume with new or same driver
        ALLOWED_TRANSITIONS.put(DeliveryStatus.PAUSED_BY_INCIDENT,
                EnumSet.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.PICKED_UP,
                           DeliveryStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(DeliveryStatus.DELIVERED,
                EnumSet.noneOf(DeliveryStatus.class));

        // FAILED can be retried (new driver assigned)
        ALLOWED_TRANSITIONS.put(DeliveryStatus.FAILED,
                EnumSet.of(DeliveryStatus.IN_TRANSIT));

        ALLOWED_TRANSITIONS.put(DeliveryStatus.CANCELLED,
                EnumSet.noneOf(DeliveryStatus.class));

        // TIMED_OUT — incident auto-created, delivery can be paused or failed
        ALLOWED_TRANSITIONS.put(DeliveryStatus.TIMED_OUT,
                EnumSet.of(DeliveryStatus.PAUSED_BY_INCIDENT, DeliveryStatus.FAILED,
                           DeliveryStatus.CANCELLED));

        // SLA_BREACHED — warning state, delivery can continue or be paused
        ALLOWED_TRANSITIONS.put(DeliveryStatus.SLA_BREACHED,
                EnumSet.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.PAUSED_BY_INCIDENT,
                           DeliveryStatus.FAILED));
    }

    private DeliveryStateTransitionPolicy() {
        // utility class — not instantiable
    }

    /**
     * Returns {@code true} if transitioning from {@code from} to {@code to} is permitted.
     */
    public static boolean isAllowed(DeliveryStatus from, DeliveryStatus to) {
        Set<DeliveryStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(
                from, EnumSet.noneOf(DeliveryStatus.class));
        return allowed.contains(to);
    }

    /**
     * Returns all valid target states reachable from the given status.
     */
    public static Set<DeliveryStatus> allowedTransitionsFrom(DeliveryStatus from) {
        Set<DeliveryStatus> result = ALLOWED_TRANSITIONS.getOrDefault(
                from, EnumSet.noneOf(DeliveryStatus.class));
        return result.isEmpty() ? EnumSet.noneOf(DeliveryStatus.class) : EnumSet.copyOf(result);
    }
}
