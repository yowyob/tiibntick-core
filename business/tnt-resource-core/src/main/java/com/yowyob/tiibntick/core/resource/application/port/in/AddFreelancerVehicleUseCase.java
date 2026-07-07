package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import reactor.core.publisher.Mono;

/**
 * Inbound port: adds a vehicle to a FreelancerOrganization's personal fleet.
 * @author MANFOUO Braun
 */
public interface AddFreelancerVehicleUseCase {
    Mono<FreelancerVehicle> addVehicle(AddFreelancerVehicleCommand cmd);
}
