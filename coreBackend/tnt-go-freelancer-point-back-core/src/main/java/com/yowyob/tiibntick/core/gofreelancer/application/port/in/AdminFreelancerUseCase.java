package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerDetailsResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for admin delivery person management use cases.
 */
public interface AdminFreelancerUseCase {

    Flux<FreelancerDetailsResponse> getPendingFreelancers();

    Flux<FreelancerDetailsResponse> getAllFreelancers(
            com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus status);

    Mono<FreelancerDetailsResponse> getFreelancerDetails(UUID id);

    Mono<Void> validateFreelancer(UUID id, boolean approved, String reason, String loginUrl);

    Mono<Void> suspendFreelancer(UUID id, String loginUrl);

    Mono<Void> revokeFreelancer(UUID id, String loginUrl);

    Mono<Void> activateFreelancer(UUID id, String loginUrl);
}
