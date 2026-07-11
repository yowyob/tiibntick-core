package com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity mapping for the {@code tnt_actor.deliverer_profiles} table.
 *
 * <p> — Added columns for tnt-incident-core integration:
 * <ul>
 *   <li>{@link #incidentHistoryCount} → {@code incident_history_count} column</li>
 *   <li>{@link #fraudFlaggedByIncidentId} → {@code fraud_flagged_by_incident_id} column</li>
 * </ul>
 * See Liquibase migration {@code V2__add_incident_tracking_to_actor.sql}.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "tnt_actor", name = "deliverer_profiles")
public record DelivererProfileEntity(
        @Id UUID id,
        UUID tenantId,
        UUID actorId,
        String actorStatus,
        String kycStatus,
        Double locationLat,
        Double locationLng,
        Double locationAccuracy,
        Instant locationTimestamp,
        String locationSource,
        double ratingScore,
        int ratingTotal,
        Instant ratingUpdatedAt,
        String badgesJson,
        Instant createdAt,
        Instant updatedAt,
        UUID agencyId,
        UUID branchId,
        UUID vehicleId,
        UUID missionActiveId,
        double capacityKg,
        UUID contractId,
        String delivererType,
        /** Number of incidents in which this deliverer was involved. Added in . */
        int incidentHistoryCount,
        /** UUID of the incident that triggered a fraud flag. Null when not flagged. Added in . */
        UUID fraudFlaggedByIncidentId,
        /** Blockchain DID anchored via tnt-trust after KYC verification. Nullable. */
        String blockchainDid) {
}
