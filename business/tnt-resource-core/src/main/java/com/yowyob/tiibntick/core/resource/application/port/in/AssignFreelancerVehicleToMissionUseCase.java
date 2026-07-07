package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import reactor.core.publisher.Mono;

/**
 * Inbound port: assigns a FreelancerVehicle to a delivery mission.
 * @author MANFOUO Braun
 */
public interface AssignFreelancerVehicleToMissionUseCase {
    Mono<FreelancerVehicle> assignToMission(AssignFreelancerVehicleToMissionCommand cmd);
}
