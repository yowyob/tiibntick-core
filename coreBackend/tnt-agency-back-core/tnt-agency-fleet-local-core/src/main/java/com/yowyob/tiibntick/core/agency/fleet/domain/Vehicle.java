package com.yowyob.tiibntick.core.agency.fleet.domain;

import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleStatus;
import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleType;

import java.time.Instant;
import java.util.UUID;

/** Ported from tnt-agency {@code Vehicle}. */
public class Vehicle {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private UUID branchId;
    private UUID assignedDelivererId;
    private final String licensePlate;
    private final String brand;
    private final String model;
    private final int year;
    private final VehicleType vehicleType;
    private VehicleStatus status;
    private Instant assignedAt;
    private Instant maintenanceStartedAt;
    private UUID coreVehicleId;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Vehicle(UUID id, UUID tenantId, UUID agencyId, UUID branchId, UUID assignedDelivererId,
                   String licensePlate, String brand, String model, int year,
                   VehicleType vehicleType, VehicleStatus status,
                   Instant assignedAt, Instant maintenanceStartedAt, UUID coreVehicleId,
                   Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.agencyId = agencyId;
        this.branchId = branchId;
        this.assignedDelivererId = assignedDelivererId;
        this.licensePlate = licensePlate;
        this.brand = brand;
        this.model = model;
        this.year = year;
        this.vehicleType = vehicleType;
        this.status = status;
        this.assignedAt = assignedAt;
        this.maintenanceStartedAt = maintenanceStartedAt;
        this.coreVehicleId = coreVehicleId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Vehicle add(UUID id, UUID tenantId, UUID agencyId, UUID branchId,
                              String licensePlate, String brand, String model,
                              int year, VehicleType type, Instant now) {
        return new Vehicle(id, tenantId, agencyId, branchId, null,
                licensePlate, brand, model, year, type,
                VehicleStatus.AVAILABLE, null, null, null, now, now, 0L);
    }

    public void assign(UUID delivererId, Instant now) {
        if (status != VehicleStatus.AVAILABLE) {
            throw new IllegalStateException("Vehicle is not available for assignment");
        }
        this.assignedDelivererId = delivererId;
        this.status = VehicleStatus.ASSIGNED;
        this.assignedAt = now;
        this.updatedAt = now;
    }

    public void unassign(Instant now) {
        this.assignedDelivererId = null;
        this.status = VehicleStatus.AVAILABLE;
        this.assignedAt = null;
        this.updatedAt = now;
    }

    public void sendToMaintenance(Instant now) {
        if (status == VehicleStatus.RETIRED) {
            throw new IllegalStateException("A retired vehicle cannot be sent to maintenance");
        }
        this.status = VehicleStatus.IN_MAINTENANCE;
        this.assignedDelivererId = null;
        this.assignedAt = null;
        this.maintenanceStartedAt = now;
        this.updatedAt = now;
    }

    public void returnFromMaintenance(Instant now) {
        if (status != VehicleStatus.IN_MAINTENANCE) {
            throw new IllegalStateException("Vehicle is not in maintenance");
        }
        this.status = VehicleStatus.AVAILABLE;
        this.maintenanceStartedAt = null;
        this.updatedAt = now;
    }

    public void retire(Instant now) {
        this.status = VehicleStatus.RETIRED;
        this.assignedDelivererId = null;
        this.assignedAt = null;
        this.updatedAt = now;
    }

    public void linkCoreVehicle(UUID coreVehicleId, Instant now) {
        if (coreVehicleId == null) {
            throw new IllegalArgumentException("coreVehicleId is required");
        }
        this.coreVehicleId = coreVehicleId;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getBranchId() { return branchId; }
    public UUID getAssignedDelivererId() { return assignedDelivererId; }
    public String getLicensePlate() { return licensePlate; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public int getYear() { return year; }
    public VehicleType getVehicleType() { return vehicleType; }
    public VehicleStatus getStatus() { return status; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getMaintenanceStartedAt() { return maintenanceStartedAt; }
    public UUID getCoreVehicleId() { return coreVehicleId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
