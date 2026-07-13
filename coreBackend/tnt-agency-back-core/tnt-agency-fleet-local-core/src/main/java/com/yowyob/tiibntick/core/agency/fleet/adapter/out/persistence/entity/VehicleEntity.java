package com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_fleet", name = "vehicles")
public class VehicleEntity {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("agency_id")
    private UUID agencyId;

    @Column("branch_id")
    private UUID branchId;

    @Column("assigned_deliverer_id")
    private UUID assignedDelivererId;

    @Column("license_plate")
    private String licensePlate;

    private String brand;
    private String model;
    private Integer year;

    @Column("vehicle_type")
    private String vehicleType;

    private String status;

    @Column("assigned_at")
    private Instant assignedAt;

    @Column("maintenance_started_at")
    private Instant maintenanceStartedAt;

    @Column("core_vehicle_id")
    private UUID coreVehicleId;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    private Long version;
}
