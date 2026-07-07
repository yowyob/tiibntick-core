package com.yowyob.tiibntick.core.actor.domain.model;

import com.yowyob.tiibntick.core.actor.domain.exception.DelivererAlreadyOnMissionException;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain model for a permanent deliverer profile in TiiBnTick.
 *
 * <p>A permanent deliverer is attached to an agency and branch, drives a vehicle,
 * and executes missions assigned by the agency manager.
 *
 * <p> — Additions for {@code tnt-incident-core} integration:
 * <ul>
 *   <li>{@link #incidentHistoryCount} — counter incremented each time an incident involving
 *       this deliverer is closed. Maintained by {@code ActorReputationPortAdapter} and the
 *       {@code IncidentEventConsumer} (Kafka consumer on {@code tnt.incident.closed}).</li>
 *   <li>{@link #fraudFlaggedByIncidentId} — nullable UUID set when the deliverer is flagged
 *       for fraud by {@code IActorReputationPort.flagForFraud()}. Also triggers
 *       {@link KycStatus#FLAGGED} on the profile.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public final class DelivererProfile extends TntActorProfile {

    private final UUID agencyId;
    private final UUID branchId;
    private final UUID vehicleId;
    private final UUID missionActiveId;
    private final double capacityKg;
    private final UUID contractId;
    private final DelivererType delivererType;

    /**
     * Number of incidents in which this deliverer was involved (as reported by tnt-incident-core).
     * Incremented at each incident closure. Used by IActorReputationPort.getIncidentHistoryCount().
     */
    private final int incidentHistoryCount;

    /**
     * UUID of the incident that triggered a fraud flag on this deliverer.
     * Null when the deliverer has no active fraud flag.
     * Set when IActorReputationPort.flagForFraud() is called by tnt-incident-core.
     */
    private final UUID fraudFlaggedByIncidentId;

    private DelivererProfile(
            UUID id,
            UUID tenantId,
            UUID actorId,
            ActorStatus actorStatus,
            KycStatus kycStatus,
            ActorLocation currentLocation,
            ActorRating rating,
            Set<Badge> badges,
            Instant createdAt,
            Instant updatedAt,
            UUID agencyId,
            UUID branchId,
            UUID vehicleId,
            UUID missionActiveId,
            double capacityKg,
            UUID contractId,
            DelivererType delivererType,
            int incidentHistoryCount,
            UUID fraudFlaggedByIncidentId) {
        super(id, tenantId, actorId, ActorType.PERMANENT_DELIVERER, actorStatus, kycStatus,
                currentLocation, rating, badges, createdAt, updatedAt);
        this.agencyId = Objects.requireNonNull(agencyId, "agencyId must not be null");
        this.branchId = Objects.requireNonNull(branchId, "branchId must not be null");
        this.vehicleId = vehicleId;
        this.missionActiveId = missionActiveId;
        if (capacityKg <= 0) {
            throw new IllegalArgumentException("capacityKg must be positive, got: " + capacityKg);
        }
        this.capacityKg = capacityKg;
        this.contractId = contractId;
        this.delivererType = delivererType != null ? delivererType : DelivererType.PERMANENT;
        this.incidentHistoryCount = Math.max(0, incidentHistoryCount);
        this.fraudFlaggedByIncidentId = fraudFlaggedByIncidentId;
    }

    // ── Factory methods ────────────────────────────────────────────────────────

    public static DelivererProfile create(
            UUID tenantId,
            UUID actorId,
            UUID agencyId,
            UUID branchId,
            double capacityKg,
            DelivererType delivererType) {
        return new DelivererProfile(
                UUID.randomUUID(), tenantId, actorId,
                ActorStatus.INACTIVE, KycStatus.PENDING,
                null, ActorRating.zero(), Set.of(),
                Instant.now(), Instant.now(),
                agencyId, branchId, null, null, capacityKg, null, delivererType,
                0, null);
    }

    public static DelivererProfile rehydrate(
            UUID id, UUID tenantId, UUID actorId,
            String actorStatus, String kycStatus,
            Double locationLat, Double locationLng, Double locationAccuracy,
            Instant locationTimestamp, String locationSource,
            double ratingScore, int ratingTotal, Instant ratingUpdatedAt,
            Set<Badge> badges,
            Instant createdAt, Instant updatedAt,
            UUID agencyId, UUID branchId, UUID vehicleId, UUID missionActiveId,
            double capacityKg, UUID contractId, String delivererType,
            int incidentHistoryCount, UUID fraudFlaggedByIncidentId) {
        ActorLocation location = (locationLat != null && locationLng != null)
                ? ActorLocation.of(locationLat, locationLng, locationAccuracy,
                        locationTimestamp != null ? locationTimestamp : Instant.now(),
                        LocationSource.from(locationSource))
                : null;
        ActorRating rating = ratingTotal > 0
                ? ActorRating.of(ratingScore, ratingTotal, ratingUpdatedAt)
                : ActorRating.zero();
        return new DelivererProfile(
                id, tenantId, actorId,
                ActorStatus.from(actorStatus), KycStatus.from(kycStatus),
                location, rating, badges, createdAt, updatedAt,
                agencyId, branchId, vehicleId, missionActiveId,
                capacityKg, contractId, DelivererType.from(delivererType),
                incidentHistoryCount, fraudFlaggedByIncidentId);
    }

    // ── Domain mutations ───────────────────────────────────────────────────────

    public DelivererProfile withLocation(ActorLocation location) {
        return new DelivererProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                location, rating(), badges(), createdAt(), Instant.now(),
                agencyId, branchId, vehicleId, missionActiveId, capacityKg, contractId,
                delivererType, incidentHistoryCount, fraudFlaggedByIncidentId);
    }

    public DelivererProfile withRating(ActorRating rating) {
        return new DelivererProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating, badges(), createdAt(), Instant.now(),
                agencyId, branchId, vehicleId, missionActiveId, capacityKg, contractId,
                delivererType, incidentHistoryCount, fraudFlaggedByIncidentId);
    }

    public DelivererProfile activate() {
        return withStatus(ActorStatus.ACTIVE);
    }

    public DelivererProfile deactivate() {
        return withStatus(ActorStatus.INACTIVE);
    }

    public DelivererProfile suspend(String reason) {
        return withStatus(ActorStatus.SUSPENDED);
    }

    private DelivererProfile withStatus(ActorStatus status) {
        return new DelivererProfile(id(), tenantId(), actorId(), status, kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                agencyId, branchId, vehicleId, missionActiveId, capacityKg, contractId,
                delivererType, incidentHistoryCount, fraudFlaggedByIncidentId);
    }

    public DelivererProfile withKycStatus(KycStatus kycStatus) {
        return new DelivererProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus,
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                agencyId, branchId, vehicleId, missionActiveId, capacityKg, contractId,
                delivererType, incidentHistoryCount, fraudFlaggedByIncidentId);
    }

    public DelivererProfile assignMission(UUID missionId) {
        if (this.missionActiveId != null) {
            throw new DelivererAlreadyOnMissionException(actorId(), this.missionActiveId);
        }
        return new DelivererProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                agencyId, branchId, vehicleId, missionId, capacityKg, contractId,
                delivererType, incidentHistoryCount, fraudFlaggedByIncidentId);
    }

    public DelivererProfile releaseMission() {
        return new DelivererProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                agencyId, branchId, vehicleId, null, capacityKg, contractId,
                delivererType, incidentHistoryCount, fraudFlaggedByIncidentId);
    }

    public DelivererProfile assignVehicle(UUID newVehicleId) {
        return new DelivererProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                agencyId, branchId, newVehicleId, missionActiveId, capacityKg, contractId,
                delivererType, incidentHistoryCount, fraudFlaggedByIncidentId);
    }

    public DelivererProfile withBadge(Badge badge) {
        Set<Badge> updatedBadges = new java.util.LinkedHashSet<>(badges());
        updatedBadges.add(badge);
        return new DelivererProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), updatedBadges, createdAt(), Instant.now(),
                agencyId, branchId, vehicleId, missionActiveId, capacityKg, contractId,
                delivererType, incidentHistoryCount, fraudFlaggedByIncidentId);
    }

    // ── Incident-related mutations (tnt-incident-core integration) ─────────────

    /**
     * Returns a new profile with the incident history counter incremented by 1.
     * Called by {@code IncidentEventConsumer} when {@code tnt.incident.closed}
     * is received and this deliverer was involved.
     *
     * @return new profile with incremented incident count
     */
    public DelivererProfile withIncrementedIncidentCount() {
        return new DelivererProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                agencyId, branchId, vehicleId, missionActiveId, capacityKg, contractId,
                delivererType, incidentHistoryCount + 1, fraudFlaggedByIncidentId);
    }

    /**
     * Returns a new profile with a fraud flag set by {@code tnt-incident-core}.
     * Also switches {@link KycStatus} to {@link KycStatus#FLAGGED} and
     * {@link ActorStatus} to {@link ActorStatus#SUSPENDED}.
     *
     * @param incidentId the incident UUID that triggered this fraud flag
     * @return new profile flagged for fraud investigation
     */
    public DelivererProfile withFraudFlag(UUID incidentId) {
        Objects.requireNonNull(incidentId, "incidentId must not be null for fraud flag");
        return new DelivererProfile(id(), tenantId(), actorId(),
                ActorStatus.SUSPENDED, KycStatus.FLAGGED,
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                agencyId, branchId, vehicleId, missionActiveId, capacityKg, contractId,
                delivererType, incidentHistoryCount, incidentId);
    }

    /**
     * Clears the fraud flag (set by an administrator after review).
     * Restores {@link KycStatus#VERIFIED} if the flag is cleared without rejection.
     *
     * @return new profile with fraud flag cleared
     */
    public DelivererProfile clearFraudFlag() {
        return new DelivererProfile(id(), tenantId(), actorId(),
                ActorStatus.INACTIVE, KycStatus.VERIFIED,
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                agencyId, branchId, vehicleId, missionActiveId, capacityKg, contractId,
                delivererType, incidentHistoryCount, null);
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public boolean isAvailableForMission() {
        return isActive() && missionActiveId == null;
    }

    public boolean hasActiveMission() {
        return missionActiveId != null;
    }

    public boolean isFlaggedForFraud() {
        return fraudFlaggedByIncidentId != null;
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public UUID agencyId() {
        return agencyId;
    }

    public UUID branchId() {
        return branchId;
    }

    public UUID vehicleId() {
        return vehicleId;
    }

    public UUID missionActiveId() {
        return missionActiveId;
    }

    public double capacityKg() {
        return capacityKg;
    }

    public UUID contractId() {
        return contractId;
    }

    public DelivererType delivererType() {
        return delivererType;
    }

    public int incidentHistoryCount() {
        return incidentHistoryCount;
    }

    public UUID fraudFlaggedByIncidentId() {
        return fraudFlaggedByIncidentId;
    }
}
