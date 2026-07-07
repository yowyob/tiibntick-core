package com.yowyob.tiibntick.core.resource.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.yowyob.tiibntick.core.resource.domain.exception.VehicleStatusTransitionException;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate Root: Vehicle.
 *
 * <p>Represents a delivery vehicle in TiiBnTick's fleet. Manages the full lifecycle from
 * registration through assignment, maintenance, and retirement.
 *
 * <p> — Additions for tnt-incident-core integration:
 * <ul>
 *   <li>{@link #hasRefrigeration} — indicates whether the vehicle has a cold chain system.
 *       Used by {@code IVehicleCompatibilityPort.getVehicleInfo()} to populate
 *       {@code VehicleInfo.hasRefrigeration} for tnt-incident-core substitution matching.</li>
 *   <li>{@link #placeInIncidentSubstitution(UUID)} — transitions to
 *       {@code VehicleStatus.IN_INCIDENT_SUBSTITUTION} for inter-agency lending.</li>
 *   <li>{@link #releaseFromIncidentSubstitution()} — returns to {@code AVAILABLE}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public final class Vehicle {

    private final UUID id;
    private final UUID tenantId;
    private final UUID organizationId;
    private final UUID agencyId;
    private final String registrationNumber;
    private final String brand;
    private final String model;
    private final int yearOfManufacture;
    private final VehicleType type;
    private final VehicleCapacity capacity;
    private final VehicleStatus status;
    private final UUID assignedDelivererId;
    private final double odometerKm;
    private final Double gpsLatitude;
    private final Double gpsLongitude;
    private final Instant lastLocationUpdate;
    private final MaintenanceSchedule nextMaintenance;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Indicates whether this vehicle has a refrigeration/cold chain system.
     * Used by tnt-incident-core's {@code IVehicleCompatibilityPort.getVehicleInfo()}
     * to match vehicles with temperature-sensitive parcel delivery requirements.
     */
    private final boolean hasRefrigeration;

    private static final Set<VehicleStatus> ASSIGNABLE_FROM = Set.of(VehicleStatus.AVAILABLE);
    private static final Set<VehicleStatus> MAINTENANCE_FROM = Set.of(
            VehicleStatus.AVAILABLE, VehicleStatus.ASSIGNED);
    private static final Set<VehicleStatus> RETIRE_FROM = Set.of(
            VehicleStatus.AVAILABLE, VehicleStatus.IN_MAINTENANCE);
    private static final Set<VehicleStatus> SUBSTITUTION_FROM = Set.of(VehicleStatus.AVAILABLE);

    private Vehicle(UUID id, UUID tenantId, UUID organizationId, UUID agencyId,
            String registrationNumber, String brand, String model, int yearOfManufacture,
            VehicleType type, VehicleCapacity capacity, VehicleStatus status,
            UUID assignedDelivererId, double odometerKm, Double gpsLatitude, Double gpsLongitude,
            Instant lastLocationUpdate, MaintenanceSchedule nextMaintenance,
            Instant createdAt, Instant updatedAt, boolean hasRefrigeration) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId is required");
        this.agencyId = Objects.requireNonNull(agencyId, "agencyId is required");
        if (registrationNumber == null || registrationNumber.isBlank()) {
            throw new IllegalArgumentException("registrationNumber must not be blank");
        }
        this.registrationNumber = registrationNumber.toUpperCase().trim();
        if (brand == null || brand.isBlank()) {
            throw new IllegalArgumentException("brand must not be blank");
        }
        this.brand = brand.trim();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        this.model = model.trim();
        this.yearOfManufacture = yearOfManufacture;
        this.type = Objects.requireNonNull(type, "type is required");
        this.capacity = Objects.requireNonNull(capacity, "capacity is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.assignedDelivererId = assignedDelivererId;
        if (odometerKm < 0) {
            throw new IllegalArgumentException("odometerKm must be >= 0, got: " + odometerKm);
        }
        this.odometerKm = odometerKm;
        this.gpsLatitude = gpsLatitude;
        this.gpsLongitude = gpsLongitude;
        this.lastLocationUpdate = lastLocationUpdate;
        this.nextMaintenance = nextMaintenance;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.hasRefrigeration = hasRefrigeration;
    }

    // ── Factory methods ───────────────────────────────────────────────

    public static Vehicle register(UUID tenantId, UUID organizationId, UUID agencyId,
            String registrationNumber, String brand, String model, int yearOfManufacture,
            VehicleType type, double maxWeightKg, double maxVolumeM3) {
        return register(tenantId, organizationId, agencyId, registrationNumber, brand, model,
                yearOfManufacture, type, maxWeightKg, maxVolumeM3, false);
    }

    public static Vehicle register(UUID tenantId, UUID organizationId, UUID agencyId,
            String registrationNumber, String brand, String model, int yearOfManufacture,
            VehicleType type, double maxWeightKg, double maxVolumeM3, boolean hasRefrigeration) {
        Instant now = Instant.now();
        return new Vehicle(UUID.randomUUID(), tenantId, organizationId, agencyId,
                registrationNumber, brand, model, yearOfManufacture, type,
                new VehicleCapacity(maxWeightKg, maxVolumeM3),
                VehicleStatus.AVAILABLE, null, 0.0, null, null, null, null, now, now,
                hasRefrigeration);
    }

    public static Vehicle rehydrate(UUID id, UUID tenantId, UUID organizationId, UUID agencyId,
            String registrationNumber, String brand, String model, int yearOfManufacture,
            VehicleType type, double maxWeightKg, double maxVolumeM3, VehicleStatus status,
            UUID assignedDelivererId, double odometerKm, Double gpsLatitude, Double gpsLongitude,
            Instant lastLocationUpdate, MaintenanceSchedule nextMaintenance,
            Instant createdAt, Instant updatedAt) {
        return rehydrate(id, tenantId, organizationId, agencyId, registrationNumber, brand, model,
                yearOfManufacture, type, maxWeightKg, maxVolumeM3, status, assignedDelivererId,
                odometerKm, gpsLatitude, gpsLongitude, lastLocationUpdate, nextMaintenance,
                createdAt, updatedAt, false);
    }

    public static Vehicle rehydrate(UUID id, UUID tenantId, UUID organizationId, UUID agencyId,
            String registrationNumber, String brand, String model, int yearOfManufacture,
            VehicleType type, double maxWeightKg, double maxVolumeM3, VehicleStatus status,
            UUID assignedDelivererId, double odometerKm, Double gpsLatitude, Double gpsLongitude,
            Instant lastLocationUpdate, MaintenanceSchedule nextMaintenance,
            Instant createdAt, Instant updatedAt, boolean hasRefrigeration) {
        return new Vehicle(id, tenantId, organizationId, agencyId, registrationNumber, brand, model,
                yearOfManufacture, type, new VehicleCapacity(maxWeightKg, maxVolumeM3), status,
                assignedDelivererId, odometerKm, gpsLatitude, gpsLongitude, lastLocationUpdate,
                nextMaintenance, createdAt, updatedAt, hasRefrigeration);
    }

    // ── Domain behaviour ──────────────────────────────────────────────

    public Vehicle assign(UUID delivererId) {
        Objects.requireNonNull(delivererId, "delivererId is required");
        ensureTransitionAllowed(VehicleStatus.ASSIGNED, ASSIGNABLE_FROM);
        return withStatus(VehicleStatus.ASSIGNED, delivererId);
    }

    public Vehicle unassign() {
        if (status != VehicleStatus.ASSIGNED) {
            throw new VehicleStatusTransitionException(id, status, VehicleStatus.AVAILABLE);
        }
        return withStatus(VehicleStatus.AVAILABLE, null);
    }

    public Vehicle sendToMaintenance(MaintenanceSchedule schedule) {
        ensureTransitionAllowed(VehicleStatus.IN_MAINTENANCE, MAINTENANCE_FROM);
        return new Vehicle(id, tenantId, organizationId, agencyId, registrationNumber, brand, model,
                yearOfManufacture, type, capacity, VehicleStatus.IN_MAINTENANCE, null, odometerKm,
                gpsLatitude, gpsLongitude, lastLocationUpdate, schedule, createdAt, Instant.now(),
                hasRefrigeration);
    }

    public Vehicle completeMaintenance() {
        if (status != VehicleStatus.IN_MAINTENANCE) {
            throw new VehicleStatusTransitionException(id, status, VehicleStatus.AVAILABLE);
        }
        return new Vehicle(id, tenantId, organizationId, agencyId, registrationNumber, brand, model,
                yearOfManufacture, type, capacity, VehicleStatus.AVAILABLE, null, odometerKm,
                gpsLatitude, gpsLongitude, lastLocationUpdate, null, createdAt, Instant.now(),
                hasRefrigeration);
    }

    public Vehicle retire() {
        ensureTransitionAllowed(VehicleStatus.RETIRED, RETIRE_FROM);
        return withStatus(VehicleStatus.RETIRED, null);
    }

    public Vehicle updateOdometer(double newOdometerKm) {
        if (newOdometerKm < odometerKm) {
            throw new IllegalArgumentException(
                    "New odometer (" + newOdometerKm + ") cannot be less than current (" + odometerKm + ")");
        }
        return new Vehicle(id, tenantId, organizationId, agencyId, registrationNumber, brand, model,
                yearOfManufacture, type, capacity, status, assignedDelivererId, newOdometerKm,
                gpsLatitude, gpsLongitude, lastLocationUpdate, nextMaintenance, createdAt, Instant.now(),
                hasRefrigeration);
    }

    public Vehicle updateLocation(double latitude, double longitude) {
        Instant now = Instant.now();
        return new Vehicle(id, tenantId, organizationId, agencyId, registrationNumber, brand, model,
                yearOfManufacture, type, capacity, status, assignedDelivererId, odometerKm,
                latitude, longitude, now, nextMaintenance, createdAt, now, hasRefrigeration);
    }

    public Vehicle scheduleNextMaintenance(MaintenanceSchedule schedule) {
        Objects.requireNonNull(schedule, "schedule is required");
        return new Vehicle(id, tenantId, organizationId, agencyId, registrationNumber, brand, model,
                yearOfManufacture, type, capacity, status, assignedDelivererId, odometerKm,
                gpsLatitude, gpsLongitude, lastLocationUpdate, schedule, createdAt, Instant.now(),
                hasRefrigeration);
    }

    // ── Incident integration (tnt-incident-core) ──────────────────────

    /**
     * Places this vehicle in temporary inter-agency substitution status.
     *
     * <p>Called by {@code VehicleCompatibilityPortAdapter} when tnt-incident-core approves
     * an {@code IncidentInterAgencyCooperation} and needs to lend this vehicle to
     * another agency's deliverer.
     *
     * <p>Only AVAILABLE vehicles can be placed in substitution.
     *
     * @param borrowingAgencyId the agency UUID that will temporarily use this vehicle
     * @return new vehicle in {@link VehicleStatus#IN_INCIDENT_SUBSTITUTION} state
     */
    public Vehicle placeInIncidentSubstitution(UUID borrowingAgencyId) {
        Objects.requireNonNull(borrowingAgencyId, "borrowingAgencyId is required");
        ensureTransitionAllowed(VehicleStatus.IN_INCIDENT_SUBSTITUTION, SUBSTITUTION_FROM);
        return new Vehicle(id, tenantId, organizationId, agencyId, registrationNumber, brand, model,
                yearOfManufacture, type, capacity, VehicleStatus.IN_INCIDENT_SUBSTITUTION,
                null, odometerKm, gpsLatitude, gpsLongitude, lastLocationUpdate, nextMaintenance,
                createdAt, Instant.now(), hasRefrigeration);
    }

    /**
     * Releases this vehicle from incident substitution, returning it to AVAILABLE.
     *
     * <p>Called when the incident is resolved and the inter-agency cooperation ends.
     *
     * @return new vehicle in {@link VehicleStatus#AVAILABLE} state
     */
    public Vehicle releaseFromIncidentSubstitution() {
        if (status != VehicleStatus.IN_INCIDENT_SUBSTITUTION) {
            throw new VehicleStatusTransitionException(id, status, VehicleStatus.AVAILABLE);
        }
        return withStatus(VehicleStatus.AVAILABLE, null);
    }

    // ── Query methods ─────────────────────────────────────────────────

    public boolean isAvailable() { return status == VehicleStatus.AVAILABLE; }
    public boolean isInIncidentSubstitution() { return status == VehicleStatus.IN_INCIDENT_SUBSTITUTION; }
    public boolean canCarry(double weightKg, double volumeM3) { return capacity.canCarry(weightKg, volumeM3); }
    public boolean isMaintenanceDue() {
        if (nextMaintenance == null) return false;
        return nextMaintenance.isDueByDate(java.time.LocalDate.now())
                || nextMaintenance.isDueByOdometer(odometerKm);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void ensureTransitionAllowed(VehicleStatus target, Set<VehicleStatus> allowedFrom) {
        if (!allowedFrom.contains(status)) {
            throw new VehicleStatusTransitionException(id, status, target);
        }
    }

    private Vehicle withStatus(VehicleStatus newStatus, UUID delivererId) {
        return new Vehicle(id, tenantId, organizationId, agencyId, registrationNumber, brand, model,
                yearOfManufacture, type, capacity, newStatus, delivererId, odometerKm,
                gpsLatitude, gpsLongitude, lastLocationUpdate, nextMaintenance, createdAt, Instant.now(),
                hasRefrigeration);
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID organizationId() { return organizationId; }
    public UUID agencyId() { return agencyId; }
    public String registrationNumber() { return registrationNumber; }
    public String brand() { return brand; }
    public String model() { return model; }
    public int yearOfManufacture() { return yearOfManufacture; }
    public VehicleType type() { return type; }
    public VehicleCapacity capacity() { return capacity; }
    public VehicleStatus status() { return status; }
    public UUID assignedDelivererId() { return assignedDelivererId; }
    public double odometerKm() { return odometerKm; }
    public Double gpsLatitude() { return gpsLatitude; }
    public Double gpsLongitude() { return gpsLongitude; }
    public Instant lastLocationUpdate() { return lastLocationUpdate; }
    public MaintenanceSchedule nextMaintenance() { return nextMaintenance; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public boolean hasRefrigeration() { return hasRefrigeration; }
}
