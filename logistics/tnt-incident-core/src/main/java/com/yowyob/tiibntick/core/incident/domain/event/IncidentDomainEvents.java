package com.yowyob.tiibntick.core.incident.domain.event;

import com.yowyob.tiibntick.core.incident.domain.enums.*;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Namespace class holding all immutable domain events published by tnt-incident-core to Kafka.
 *
 * <p>Each nested class represents one event type and is serialised to JSON before being
 * sent to its dedicated Kafka topic by
 * {@link com.yowyob.tiibntick.core.incident.adapter.kafka.IncidentKafkaEventPublisher}.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */
public final class IncidentDomainEvents {

    private IncidentDomainEvents() {}

    /**
     * Published to {@code tnt.incident.created} when a new incident is created and acknowledged.
     * Consumed by tnt-delivery-core (mission pause), tnt-trust (chain init) and tnt-notify-core.
     */
    @Value
    @Builder
    public static class IncidentCreatedEvent {
        UUID eventId;
        UUID incidentId;
        String referenceCode;
        UUID tenantId;
        UUID agencyId;
        UUID missionId;
        PlatformType platform;
        IncidentCategory category;
        IncidentType type;
        String description;
        UUID reportedByActorId;
        ActorRole reportedByRole;
        List<UUID> affectedParcelIds;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.status.changed} on every state transition.
     * Consumed by all monitoring dashboards and tnt-notify-core.
     */
    @Value
    @Builder
    public static class IncidentStatusChangedEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID missionId;
        UUID agencyId;
        PlatformType platform;
        IncidentStatus previousStatus;
        IncidentStatus newStatus;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.triaged} once severity, risk score and geo-snapshot
     * have been computed. Consumed by analytics and agency dashboards.
     */
    @Value
    @Builder
    public static class IncidentTriagedEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID missionId;
        IncidentSeverity severity;
        IncidentCategory category;
        double riskScore;
        boolean autoResolutionRecommended;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.driver.assigned} when a replacement driver is confirmed.
     * Consumed by tnt-delivery-core (mission driver update) and tnt-notify-core.
     */
    @Value
    @Builder
    public static class IncidentDriverAssignedEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID missionId;
        UUID originalDriverId;
        UUID replacementDriverId;
        UUID replacementVehicleId;
        UUID replacementAgencyId;
        double handoverLat;
        double handoverLng;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.handover.completed} when both drivers confirm the
     * physical parcel handover. Consumed by tnt-delivery-core (mission resume) and tnt-trust.
     */
    @Value
    @Builder
    public static class HandoverCompletedEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID missionId;
        UUID originalDriverId;
        UUID replacementDriverId;
        String blockchainTxHash;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.resolved} when the incident transitions to RESOLVED.
     * Consumed by tnt-billing-wallet (unfreeze), tnt-actor-core (reputation update) and analytics.
     */
    @Value
    @Builder
    public static class IncidentResolvedEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID missionId;
        UUID agencyId;
        PlatformType platform;
        ResolutionMode resolutionMode;
        long durationMinutes;
        boolean slaBreached;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.closed} when the incident is fully archived.
     * Consumed by tnt-trust (chain seal) and tnt-media-core (evidence archive).
     */
    @Value
    @Builder
    public static class IncidentClosedEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID missionId;
        UUID agencyId;
        PlatformType platform;
        List<UUID> affectedParcelIds;
        boolean multiParcel;
        String incidentBlockchainChainId;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.cancelled} when the incident is cancelled.
     * Consumed by tnt-billing-wallet (unfreeze) and tnt-notify-core.
     */
    @Value
    @Builder
    public static class IncidentCancelledEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID missionId;
        String reason;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.escalated} when the incident is escalated.
     * Consumed by tnt-notify-core (agency manager alert) and analytics.
     */
    @Value
    @Builder
    public static class IncidentEscalatedEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID missionId;
        UUID agencyId;
        int escalationLevel;
        ActorRole escalatedToRole;
        IncidentSeverity severity;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.escalated.to.dispute} when a fraud incident is escalated
     * to a formal dispute. Consumed by tnt-dispute-core to auto-create a Dispute record.
     */
    @Value
    @Builder
    public static class IncidentEscalatedToDisputeEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID missionId;
        UUID agencyId;
        List<UUID> affectedParcelIds;
        String fraudReason;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.interagency.requested} when an agency requests
     * cooperation from another agency.
     */
    @Value
    @Builder
    public static class InterAgencyCoopRequestedEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID requestingAgencyId;
        UUID respondingAgencyId;
        CooperationType cooperationType;
        String details;
        Instant occurredAt;
    }

    /**
     * Published to {@code tnt.incident.interagency.completed} when the inter-agency
     * cooperation concludes successfully and is blockchain-anchored.
     */
    @Value
    @Builder
    public static class InterAgencyCoopCompletedEvent {
        UUID eventId;
        UUID incidentId;
        UUID tenantId;
        UUID requestingAgencyId;
        UUID respondingAgencyId;
        String blockchainTxHash;
        Instant occurredAt;
    }
}
