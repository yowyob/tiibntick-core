package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Personal vehicles of independent couriers (owner = an actor, no organization).
 */
public interface AddPersonalVehicleUseCase {

    /**
     * Registers the vehicle. At most {@link FreelancerVehicle#MAX_VEHICLES_PER_ORG} vehicles per actor; a plate
     * already registered on any vehicle is refused ({@link IllegalStateException}).
     */
    Mono<FreelancerVehicle> addPersonalVehicle(AddPersonalVehicleCommand cmd);

    /** The vehicles owned by the actor, oldest first. */
    Flux<FreelancerVehicle> getPersonalVehicles(UUID ownerActorId);
}
