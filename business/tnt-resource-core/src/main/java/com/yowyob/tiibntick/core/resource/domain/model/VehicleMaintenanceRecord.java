package com.yowyob.tiibntick.core.resource.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity capturing a completed or in-progress maintenance intervention on a vehicle.
 * Immutable — transitions produce new instances.
 *
 * @author MANFOUO Braun.
 */
public final class VehicleMaintenanceRecord {

    private final UUID id;
    private final UUID vehicleId;
    private final UUID tenantId;
    private final UUID agencyId;
    private final MaintenanceType type;
    private final String description;
    private final double odometerAtMaintenanceKm;
    private final LocalDate scheduledDate;
    private final LocalDate completedDate;
    private final String technicianName;
    private final Instant createdAt;
    private final Instant updatedAt;

    private VehicleMaintenanceRecord(UUID id, UUID vehicleId, UUID tenantId, UUID agencyId,
            MaintenanceType type, String description, double odometerAtMaintenanceKm,
            LocalDate scheduledDate, LocalDate completedDate, String technicianName,
            Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.agencyId = Objects.requireNonNull(agencyId, "agencyId is required");
        this.type = Objects.requireNonNull(type, "type is required");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        this.description = description.trim();
        if (odometerAtMaintenanceKm < 0) {
            throw new IllegalArgumentException("odometerAtMaintenanceKm must be >= 0");
        }
        this.odometerAtMaintenanceKm = odometerAtMaintenanceKm;
        this.scheduledDate = scheduledDate;
        this.completedDate = completedDate;
        this.technicianName = technicianName;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    /**
     * Creates a new pending maintenance record (no completedDate yet).
     */
    public static VehicleMaintenanceRecord create(UUID tenantId, UUID agencyId, UUID vehicleId,
            MaintenanceType type, String description, double odometerKm, LocalDate scheduledDate,
            String technicianName) {
        Instant now = Instant.now();
        return new VehicleMaintenanceRecord(UUID.randomUUID(), vehicleId, tenantId, agencyId,
                type, description, odometerKm, scheduledDate, null, technicianName, now, now);
    }

    /** Rehydrates a persisted entity from storage. */
    public static VehicleMaintenanceRecord rehydrate(UUID id, UUID vehicleId, UUID tenantId, UUID agencyId,
            MaintenanceType type, String description, double odometerKm, LocalDate scheduledDate,
            LocalDate completedDate, String technicianName, Instant createdAt, Instant updatedAt) {
        return new VehicleMaintenanceRecord(id, vehicleId, tenantId, agencyId, type, description,
                odometerKm, scheduledDate, completedDate, technicianName, createdAt, updatedAt);
    }

    /** Marks this maintenance record as completed on the given date. */
    public VehicleMaintenanceRecord complete(LocalDate completionDate) {
        Objects.requireNonNull(completionDate, "completionDate is required");
        return new VehicleMaintenanceRecord(id, vehicleId, tenantId, agencyId, type, description,
                odometerAtMaintenanceKm, scheduledDate, completionDate, technicianName, createdAt, Instant.now());
    }

    public boolean isCompleted() {
        return completedDate != null;
    }

    public UUID id() { return id; }
    public UUID vehicleId() { return vehicleId; }
    public UUID tenantId() { return tenantId; }
    public UUID agencyId() { return agencyId; }
    public MaintenanceType type() { return type; }
    public String description() { return description; }
    public double odometerAtMaintenanceKm() { return odometerAtMaintenanceKm; }
    public LocalDate scheduledDate() { return scheduledDate; }
    public LocalDate completedDate() { return completedDate; }
    public String technicianName() { return technicianName; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
