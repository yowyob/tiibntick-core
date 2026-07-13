package com.yowyob.tiibntick.core.agency.fleet.adapter.out.clients;

import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleType;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ResourceCorePort {

    Mono<UUID> registerVehicle(RegisterVehicleRequest request);

    record RegisterVehicleRequest(
            UUID tenantId,
            UUID kernelOrganizationId,
            UUID coreAgencyId,
            String registrationNumber,
            String brand,
            String model,
            int yearOfManufacture,
            VehicleType type) {}
}
