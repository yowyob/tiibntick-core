package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port: deactivates a FreelancerVehicle (vehicle sold, scrapped, or long-term storage).
 * @author MANFOUO Braun
 */
public interface DeactivateFreelancerVehicleUseCase {
    Mono<FreelancerVehicle> deactivateVehicle(UUID vehicleId);
}
