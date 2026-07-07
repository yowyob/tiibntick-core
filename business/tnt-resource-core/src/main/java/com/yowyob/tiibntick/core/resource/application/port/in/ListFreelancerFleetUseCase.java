package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.EquipmentType;
import com.yowyob.tiibntick.core.resource.domain.model.FreelancerEquipment;
import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port: queries the FreelancerOrg fleet (vehicles and equipment).
 * @author MANFOUO Braun
 */
public interface ListFreelancerFleetUseCase {

    /** Returns all vehicles (active and inactive) belonging to the given FreelancerOrg. */
    Flux<FreelancerVehicle> getAllVehicles(UUID ownerOrgId);

    /** Returns only available (active + not on mission) vehicles. */
    Flux<FreelancerVehicle> getAvailableVehicles(UUID ownerOrgId);

    /** Returns all active equipment belonging to the given FreelancerOrg. */
    Flux<FreelancerEquipment> getActiveEquipments(UUID ownerOrgId);

    /** Checks if the org has at least one active equipment of the given type. */
    Mono<Boolean> hasEquipmentOfType(UUID ownerOrgId, EquipmentType type);

    /** Checks if a vehicle has enough capacity for the given weight and volume. */
    Mono<Boolean> hasCapacityFor(UUID vehicleId, double weightKg, double volumeM3);
}
