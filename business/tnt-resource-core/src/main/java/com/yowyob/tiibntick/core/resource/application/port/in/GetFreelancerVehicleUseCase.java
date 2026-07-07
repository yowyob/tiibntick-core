package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port: retrieves a single FreelancerVehicle by its UUID.
 * @author MANFOUO Braun
 */
public interface GetFreelancerVehicleUseCase {
    Mono<FreelancerVehicle> getVehicle(UUID vehicleId);
}
