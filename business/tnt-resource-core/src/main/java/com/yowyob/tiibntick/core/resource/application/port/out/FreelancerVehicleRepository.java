package com.yowyob.tiibntick.core.resource.application.port.out;

import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port: persistence contract for {@link FreelancerVehicle}.
 *
 * @author MANFOUO Braun
 */
public interface FreelancerVehicleRepository {

    Mono<FreelancerVehicle> save(FreelancerVehicle vehicle);

    Mono<FreelancerVehicle> findById(UUID vehicleId);

    Flux<FreelancerVehicle> findByOwnerOrgId(UUID ownerOrgId);

    Flux<FreelancerVehicle> findAvailableByOwnerOrgId(UUID ownerOrgId);

    Mono<Long> countByOwnerOrgId(UUID ownerOrgId);

    Mono<Boolean> existsByOwnerOrgIdAndPlateNumber(UUID ownerOrgId, String plateNumber);

    Mono<Void> deleteById(UUID vehicleId);
}
