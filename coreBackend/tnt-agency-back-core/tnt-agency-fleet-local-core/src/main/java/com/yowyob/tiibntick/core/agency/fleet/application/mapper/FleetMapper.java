package com.yowyob.tiibntick.core.agency.fleet.application.mapper;

import com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence.entity.VehicleEntity;
import com.yowyob.tiibntick.core.agency.fleet.domain.Vehicle;
import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleStatus;
import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleType;

public final class FleetMapper {

    private FleetMapper() {}

    public static VehicleEntity toEntity(Vehicle v) {
        VehicleEntity e = new VehicleEntity();
        e.setId(v.getId());
        e.setTenantId(v.getTenantId());
        e.setAgencyId(v.getAgencyId());
        e.setBranchId(v.getBranchId());
        e.setAssignedDelivererId(v.getAssignedDelivererId());
        e.setLicensePlate(v.getLicensePlate());
        e.setBrand(v.getBrand());
        e.setModel(v.getModel());
        e.setYear(v.getYear());
        e.setVehicleType(v.getVehicleType().name());
        e.setStatus(v.getStatus().name());
        e.setAssignedAt(v.getAssignedAt());
        e.setMaintenanceStartedAt(v.getMaintenanceStartedAt());
        e.setCoreVehicleId(v.getCoreVehicleId());
        e.setCreatedAt(v.getCreatedAt());
        e.setUpdatedAt(v.getUpdatedAt());
        e.setVersion(v.getVersion());
        return e;
    }

    public static Vehicle toDomain(VehicleEntity e) {
        return new Vehicle(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getBranchId(),
                e.getAssignedDelivererId(), e.getLicensePlate(), e.getBrand(), e.getModel(),
                e.getYear() != null ? e.getYear() : 0,
                VehicleType.valueOf(e.getVehicleType()),
                VehicleStatus.valueOf(e.getStatus()),
                e.getAssignedAt(), e.getMaintenanceStartedAt(), e.getCoreVehicleId(),
                e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion() != null ? e.getVersion() : 0L);
    }
}
