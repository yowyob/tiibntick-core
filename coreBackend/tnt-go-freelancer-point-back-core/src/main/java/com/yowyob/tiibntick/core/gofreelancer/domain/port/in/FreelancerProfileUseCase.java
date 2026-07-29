package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerUpdateRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerDetailsResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for delivery person profile management use cases.
 */
public interface FreelancerProfileUseCase {

    Mono<FreelancerDetailsResponse> getProfile(UUID id);

    Mono<Void> updateProfile(UUID id, FreelancerUpdateRequest request);

    Mono<Void> deleteProfile(UUID id);
}
