package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import reactor.core.publisher.Mono;

/**
 * Updates a vehicle's declared data (lot L3). An unknown vehicle raises {@code FreelancerVehicleNotFoundException};
 * a plate already used by another vehicle raises {@link IllegalStateException}.
 */
public interface UpdateFreelancerVehicleUseCase {

    Mono<FreelancerVehicle> updateVehicle(UpdateFreelancerVehicleCommand cmd);
}
