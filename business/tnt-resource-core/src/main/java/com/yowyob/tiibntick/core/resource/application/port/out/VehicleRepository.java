package com.yowyob.tiibntick.core.resource.application.port.out;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port: persistence contract for Vehicle aggregate.
 * @author MANFOUO Braun.
 */
public interface VehicleRepository {

    Mono<Vehicle> save(Vehicle vehicle);

    Mono<Vehicle> findById(UUID tenantId, UUID vehicleId);

    Flux<Vehicle> findByAgency(UUID tenantId, UUID agencyId);

    Flux<Vehicle> findByAgencyAndStatus(UUID tenantId, UUID agencyId, VehicleStatus status);

    Flux<Vehicle> findAvailableByAgency(UUID tenantId, UUID agencyId);

    Mono<Boolean> existsByRegistrationNumber(UUID tenantId, String registrationNumber);

    Mono<Long> countByAgencyAndStatus(UUID tenantId, UUID agencyId, VehicleStatus status);

    /**
     * Finds a vehicle by ID without tenant scoping.
     * Used by {@code IVehicleCompatibilityPort} from tnt-incident-core.
     */
    Mono<Vehicle> findByIdNoTenant(UUID vehicleId);

    /**
     * Finds AVAILABLE vehicles in a tenant (optionally agency-scoped) with
     * sufficient capacity, sorted by ascending haversine distance from (lat, lng).
     * Used by {@code IDriverAvailabilityPort.findEligibleReplacementDrivers()} from
     * tnt-incident-core for replacement driver matching.
     *
     * @param tenantId          tenant scope
     * @param agencyId          optional agency filter (null = all agencies in tenant)
     * @param latitude          incident location latitude
     * @param longitude         incident location longitude
     * @param requiredCapacityKg minimum vehicle capacity in kg
     * @param vehicleCategory   optional VehicleType name filter (null = all types)
     * @return vehicles sorted by distance from incident location
     */
    Flux<Vehicle> findAvailableNear(UUID tenantId, UUID agencyId,
                                    double latitude, double longitude,
                                    double requiredCapacityKg, String vehicleCategory);
}

