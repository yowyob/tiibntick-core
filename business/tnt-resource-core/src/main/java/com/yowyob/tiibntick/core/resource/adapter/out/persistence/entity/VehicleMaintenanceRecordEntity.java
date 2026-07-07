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
 * R2DBC persistence entity for VehicleMaintenanceRecord.
 * Maps to the {@code tnt_vehicle_maintenance_records} table.
 *
 * @author MANFOUO Braun.
 */
@Table("tnt_vehicle_maintenance_records")
public class VehicleMaintenanceRecordEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    @Column("vehicle_id")     private UUID vehicleId;
    @Column("tenant_id")      private UUID tenantId;
    @Column("agency_id")      private UUID agencyId;
    @Column("type")           private String type;
    @Column("description")    private String description;
    @Column("odometer_km")    private double odometerKm;
    @Column("scheduled_date") private LocalDate scheduledDate;
    @Column("completed_date") private LocalDate completedDate;
    @Column("technician_name") private String technicianName;
    @Column("created_at")     private Instant createdAt;
    @Column("updated_at")     private Instant updatedAt;

    public static VehicleMaintenanceRecordEntity fromDomain(VehicleMaintenanceRecord rec) {
        VehicleMaintenanceRecordEntity e = new VehicleMaintenanceRecordEntity();
        e.id = rec.id();
        e.vehicleId = rec.vehicleId();
        e.tenantId = rec.tenantId();
        e.agencyId = rec.agencyId();
        e.type = rec.type().name();
        e.description = rec.description();
        e.odometerKm = rec.odometerAtMaintenanceKm();
        e.scheduledDate = rec.scheduledDate();
        e.completedDate = rec.completedDate();
        e.technicianName = rec.technicianName();
        e.createdAt = rec.createdAt();
        e.updatedAt = rec.updatedAt();
        return e;
    }

    public VehicleMaintenanceRecord toDomain() {
        return VehicleMaintenanceRecord.rehydrate(id, vehicleId, tenantId, agencyId,
                MaintenanceType.valueOf(type), description, odometerKm,
                scheduledDate, completedDate, technicianName, createdAt, updatedAt);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getOdometerKm() { return odometerKm; }
    public void setOdometerKm(double odometerKm) { this.odometerKm = odometerKm; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public LocalDate getCompletedDate() { return completedDate; }
    public void setCompletedDate(LocalDate completedDate) { this.completedDate = completedDate; }
    public String getTechnicianName() { return technicianName; }
    public void setTechnicianName(String technicianName) { this.technicianName = technicianName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
