package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enriched delivery status-changed event published to {@code tnt.delivery.mission.status.changed}.
 *
 * <p> — Added FreelancerOrg context fields:
 * <ul>
 *   <li>{@link #freelancerOrgId} — the FreelancerOrg executing this delivery (may be null).</li>
 *   <li>{@link #freelancerRole} — OWNER or SUB_DELIVERER role.</li>
 * </ul>
 *
 * <p>Consumed by {@code tnt-incident-core}'s {@code IncidentEventConsumer} for SLA monitoring.
 *
 * @author MANFOUO Braun
 */
public record MissionStatusChangedEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,

        /** The new delivery/mission status (e.g., "IN_TRANSIT", "TIMED_OUT", "DELIVERED"). */
        String newStatus,

        /** The previous status before this transition. */
        String previousStatus,

        /** The delivery person (driver) UUID, may be null if not yet assigned. */
        UUID deliveryPersonId,

        /**
         * Agency UUID this delivery belongs to.
         * Null for freelancer deliveries or Go platform.
         */
        UUID agencyId,

        /** The delivery ID (= mission ID in tnt-incident-core terminology). */
        UUID missionId,

        /**
         * Operational platform generating this event.
         * Values: "GO" | "FREELANCER" | "POINT" | "AGENCY"
         */
        String platform,

        /** List of parcel UUIDs carried in this mission. */
        List<UUID> parcelIds,

        // ── : FreelancerOrg context ──────────────────────────────────────

        /**
         * UUID of the FreelancerOrganization executing this delivery.
         * Null when executed by an Agency (platform="AGENCY") or Go platform.
         * References tnt-organization-core UUID — pure integration key.
         */
        String freelancerOrgId,

        /**
         * Role of the FreelancerOrg member executing: "OWNER" or "SUB_DELIVERER".
         * Null when freelancerOrgId is null.
         */
        String freelancerRole,

        Instant occurredAt
) implements DeliveryDomainEvent {

    /** Convenience constructor for standard single-parcel Agency deliveries (backward compat). */
    public MissionStatusChangedEvent(UUID deliveryId, UUID tenantId,
                                      String newStatus, String previousStatus,
                                      UUID deliveryPersonId, UUID agencyId,
                                      String platform, UUID parcelId,
                                      Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId,
                newStatus, previousStatus, deliveryPersonId, agencyId,
                deliveryId, platform,
                parcelId != null ? List.of(parcelId) : List.of(),
                null, null,  // no freelancer context
                occurredAt);
    }

    /** Convenience constructor for FreelancerOrg deliveries (). */
    public MissionStatusChangedEvent(UUID deliveryId, UUID tenantId,
                                      String newStatus, String previousStatus,
                                      UUID deliveryPersonId, UUID agencyId,
                                      String platform, UUID parcelId,
                                      String freelancerOrgId, String freelancerRole,
                                      Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId,
                newStatus, previousStatus, deliveryPersonId, agencyId,
                deliveryId, platform,
                parcelId != null ? List.of(parcelId) : List.of(),
                freelancerOrgId, freelancerRole,
                occurredAt);
    }
}
