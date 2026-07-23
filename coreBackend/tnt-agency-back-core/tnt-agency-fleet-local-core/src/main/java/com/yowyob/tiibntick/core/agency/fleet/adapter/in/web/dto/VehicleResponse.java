package com.yowyob.tiibntick.core.agency.fleet.adapter.in.web.dto;

import com.yowyob.tiibntick.core.agency.fleet.domain.Vehicle;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
        UUID id, UUID tenantId, UUID agencyId, UUID branchId,
        UUID assignedDelivererId, String licensePlate, String brand,
        String model, int year, String vehicleType, String status,
        UUID coreVehicleId,
        String source,
        String fleetmanVehicleId,
        Instant lastSyncedAt,
        Instant assignedAt, Instant maintenanceStartedAt, Instant createdAt) {

    public static VehicleResponse from(Vehicle v) {
        return new VehicleResponse(
                v.getId(), v.getTenantId(), v.getAgencyId(), v.getBranchId(),
                v.getAssignedDelivererId(), v.getLicensePlate(), v.getBrand(),
                v.getModel(), v.getYear(), v.getVehicleType().name(), v.getStatus().name(),
                v.getCoreVehicleId(),
                v.getSource() != null ? v.getSource().name() : "AGENCY",
                v.getFleetmanVehicleId(),
                v.getLastSyncedAt(),
                v.getAssignedAt(), v.getMaintenanceStartedAt(), v.getCreatedAt());
    }
}
