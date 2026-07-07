package com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.resource.domain.model.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * R2DBC persistence entity for the Vehicle aggregate root.
 * Maps to the {@code tnt_vehicles} table in the core_db schema.
 *
 * @author MANFOUO Braun.
 */
@Table("tnt_vehicles")
public class VehicleEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    @Column("tenant_id")       private UUID tenantId;
    @Column("organization_id") private UUID organizationId;
    @Column("agency_id")       private UUID agencyId;
    @Column("registration_number") private String registrationNumber;
    @Column("brand")           private String brand;
    @Column("model")           private String model;
    @Column("year_of_manufacture") private int yearOfManufacture;
    @Column("type")            private String type;
    @Column("max_weight_kg")   private double maxWeightKg;
    @Column("max_volume_m3")   private double maxVolumeM3;
    @Column("status")          private String status;
    @Column("assigned_deliverer_id") private UUID assignedDelivererId;
    @Column("odometer_km")     private double odometerKm;
    @Column("gps_latitude")    private Double gpsLatitude;
    @Column("gps_longitude")   private Double gpsLongitude;
    @Column("last_location_update") private Instant lastLocationUpdate;
    @Column("next_maintenance_date") private LocalDate nextMaintenanceDate;
    @Column("next_maintenance_type") private String nextMaintenanceType;
    @Column("next_maintenance_reason") private String nextMaintenanceReason;
    @Column("next_maintenance_odometer_threshold") private Double nextMaintenanceOdometerThreshold;
    @Column("created_at")      private Instant createdAt;
    @Column("updated_at")      private Instant updatedAt;
    /** Whether this vehicle has a cold chain/refrigeration system (tnt-incident-core ). */
    @Column("has_refrigeration") private boolean hasRefrigeration;

    public static VehicleEntity fromDomain(Vehicle vehicle) {
        VehicleEntity e = new VehicleEntity();
        e.id = vehicle.id();
        e.tenantId = vehicle.tenantId();
        e.organizationId = vehicle.organizationId();
        e.agencyId = vehicle.agencyId();
        e.registrationNumber = vehicle.registrationNumber();
        e.brand = vehicle.brand();
        e.model = vehicle.model();
        e.yearOfManufacture = vehicle.yearOfManufacture();
        e.type = vehicle.type().name();
        e.maxWeightKg = vehicle.capacity().maxWeightKg();
        e.maxVolumeM3 = vehicle.capacity().maxVolumeM3();
        e.status = vehicle.status().name();
        e.assignedDelivererId = vehicle.assignedDelivererId();
        e.odometerKm = vehicle.odometerKm();
        e.gpsLatitude = vehicle.gpsLatitude();
        e.gpsLongitude = vehicle.gpsLongitude();
        e.lastLocationUpdate = vehicle.lastLocationUpdate();
        if (vehicle.nextMaintenance() != null) {
            e.nextMaintenanceDate = vehicle.nextMaintenance().scheduledDate();
            e.nextMaintenanceType = vehicle.nextMaintenance().type().name();
            e.nextMaintenanceReason = vehicle.nextMaintenance().reason();
            e.nextMaintenanceOdometerThreshold = vehicle.nextMaintenance().odometerThresholdKm();
        }
        e.createdAt = vehicle.createdAt();
        e.updatedAt = vehicle.updatedAt();
        e.hasRefrigeration = vehicle.hasRefrigeration();
        return e;
    }

    public Vehicle toDomain() {
        MaintenanceSchedule schedule = nextMaintenanceDate != null
                ? new MaintenanceSchedule(nextMaintenanceDate,
                MaintenanceType.valueOf(nextMaintenanceType),
                nextMaintenanceReason,
                nextMaintenanceOdometerThreshold)
                : null;
        return Vehicle.rehydrate(id, tenantId, organizationId, agencyId,
                registrationNumber, brand, model, yearOfManufacture,
                VehicleType.valueOf(type), maxWeightKg, maxVolumeM3,
                VehicleStatus.valueOf(status), assignedDelivererId, odometerKm,
                gpsLatitude, gpsLongitude, lastLocationUpdate, schedule, createdAt, updatedAt,
                hasRefrigeration);
    }

    // --- Getters & Setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getYearOfManufacture() { return yearOfManufacture; }
    public void setYearOfManufacture(int yearOfManufacture) { this.yearOfManufacture = yearOfManufacture; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public double getMaxWeightKg() { return maxWeightKg; }
    public void setMaxWeightKg(double maxWeightKg) { this.maxWeightKg = maxWeightKg; }
    public double getMaxVolumeM3() { return maxVolumeM3; }
    public void setMaxVolumeM3(double maxVolumeM3) { this.maxVolumeM3 = maxVolumeM3; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getAssignedDelivererId() { return assignedDelivererId; }
    public void setAssignedDelivererId(UUID assignedDelivererId) { this.assignedDelivererId = assignedDelivererId; }
    public double getOdometerKm() { return odometerKm; }
    public void setOdometerKm(double odometerKm) { this.odometerKm = odometerKm; }
    public Double getGpsLatitude() { return gpsLatitude; }
    public void setGpsLatitude(Double gpsLatitude) { this.gpsLatitude = gpsLatitude; }
    public Double getGpsLongitude() { return gpsLongitude; }
    public void setGpsLongitude(Double gpsLongitude) { this.gpsLongitude = gpsLongitude; }
    public Instant getLastLocationUpdate() { return lastLocationUpdate; }
    public void setLastLocationUpdate(Instant lastLocationUpdate) { this.lastLocationUpdate = lastLocationUpdate; }
    public LocalDate getNextMaintenanceDate() { return nextMaintenanceDate; }
    public void setNextMaintenanceDate(LocalDate nextMaintenanceDate) { this.nextMaintenanceDate = nextMaintenanceDate; }
    public String getNextMaintenanceType() { return nextMaintenanceType; }
    public void setNextMaintenanceType(String nextMaintenanceType) { this.nextMaintenanceType = nextMaintenanceType; }
    public String getNextMaintenanceReason() { return nextMaintenanceReason; }
    public void setNextMaintenanceReason(String nextMaintenanceReason) { this.nextMaintenanceReason = nextMaintenanceReason; }
    public Double getNextMaintenanceOdometerThreshold() { return nextMaintenanceOdometerThreshold; }
    public void setNextMaintenanceOdometerThreshold(Double nextMaintenanceOdometerThreshold) { this.nextMaintenanceOdometerThreshold = nextMaintenanceOdometerThreshold; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isHasRefrigeration() { return hasRefrigeration; }
    public void setHasRefrigeration(boolean hasRefrigeration) { this.hasRefrigeration = hasRefrigeration; }
}

