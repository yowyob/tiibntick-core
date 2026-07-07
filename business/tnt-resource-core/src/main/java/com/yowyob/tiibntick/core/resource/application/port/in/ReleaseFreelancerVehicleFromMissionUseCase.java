package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port: releases a FreelancerVehicle from its current mission, making it available again.
 * @author MANFOUO Braun
 */
public interface ReleaseFreelancerVehicleFromMissionUseCase {
    Mono<FreelancerVehicle> releaseFromMission(UUID vehicleId, String missionId);
}
