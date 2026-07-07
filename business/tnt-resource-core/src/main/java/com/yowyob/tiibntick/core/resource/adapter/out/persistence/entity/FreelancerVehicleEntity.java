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
 * R2DBC persistence entity for the FreelancerVehicle domain entity.
 * Maps to the {@code tnt_freelancer_vehicles} table.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_freelancer_vehicles")
public class FreelancerVehicleEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    @Column("freelancer_org_id")          private UUID freelancerOrgId;
    @Column("vehicle_type")               private String vehicleType;
    @Column("brand")                      private String brand;
    @Column("model")                      private String model;
    @Column("plate_number")               private String plateNumber;
    @Column("max_capacity_kg")            private double maxCapacityKg;
    @Column("volume_m3")                  private Double volumeM3;
    @Column("fuel_type")                  private String fuelType;
    @Column("fuel_consumption_l_per_100km") private Double fuelConsumptionLPer100km;
    @Column("registration_doc_ref")       private String registrationDocRef;
    @Column("insurance_doc_ref")          private String insuranceDocRef;
    @Column("is_active")                  private boolean active;
    @Column("last_maintenance_at")        private LocalDate lastMaintenanceAt;
    @Column("current_mission_id")         private String currentMissionId;
    @Column("created_at")                 private Instant createdAt;
    @Column("updated_at")                 private Instant updatedAt;

    public static FreelancerVehicleEntity fromDomain(FreelancerVehicle v) {
        FreelancerVehicleEntity e = new FreelancerVehicleEntity();
        e.id = v.vehicleId();
        e.freelancerOrgId = v.ownerOrgId();
        e.vehicleType = v.type().name();
        e.brand = v.brand();
        e.model = v.model();
        e.plateNumber = v.plateNumber();
        e.maxCapacityKg = v.maxCapacityKg();
        e.volumeM3 = v.volumeM3();
        e.fuelType = v.fuelType() != null ? v.fuelType().name() : null;
        e.fuelConsumptionLPer100km = v.fuelConsumptionLPer100km();
        e.registrationDocRef = v.registrationDocRef();
        e.insuranceDocRef = v.insuranceDocRef();
        e.active = v.isActive();
        e.lastMaintenanceAt = v.lastMaintenanceAt();
        e.currentMissionId = v.currentMissionId();
        e.createdAt = v.createdAt();
        e.updatedAt = v.updatedAt();
        return e;
    }

    public FreelancerVehicle toDomain() {
        return FreelancerVehicle.rehydrate(
                id, freelancerOrgId, VehicleType.valueOf(vehicleType),
                brand, model, plateNumber, maxCapacityKg, volumeM3,
                fuelType != null ? FuelType.valueOf(fuelType) : null,
                fuelConsumptionLPer100km, registrationDocRef, insuranceDocRef,
                active, lastMaintenanceAt, currentMissionId, createdAt, updatedAt);
    }

    // --- Getters & Setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getFreelancerOrgId() { return freelancerOrgId; }
    public void setFreelancerOrgId(UUID freelancerOrgId) { this.freelancerOrgId = freelancerOrgId; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public double getMaxCapacityKg() { return maxCapacityKg; }
    public void setMaxCapacityKg(double maxCapacityKg) { this.maxCapacityKg = maxCapacityKg; }
    public Double getVolumeM3() { return volumeM3; }
    public void setVolumeM3(Double volumeM3) { this.volumeM3 = volumeM3; }
    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }
    public Double getFuelConsumptionLPer100km() { return fuelConsumptionLPer100km; }
    public void setFuelConsumptionLPer100km(Double v) { this.fuelConsumptionLPer100km = v; }
    public String getRegistrationDocRef() { return registrationDocRef; }
    public void setRegistrationDocRef(String v) { this.registrationDocRef = v; }
    public String getInsuranceDocRef() { return insuranceDocRef; }
    public void setInsuranceDocRef(String v) { this.insuranceDocRef = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDate getLastMaintenanceAt() { return lastMaintenanceAt; }
    public void setLastMaintenanceAt(LocalDate v) { this.lastMaintenanceAt = v; }
    public String getCurrentMissionId() { return currentMissionId; }
    public void setCurrentMissionId(String v) { this.currentMissionId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
