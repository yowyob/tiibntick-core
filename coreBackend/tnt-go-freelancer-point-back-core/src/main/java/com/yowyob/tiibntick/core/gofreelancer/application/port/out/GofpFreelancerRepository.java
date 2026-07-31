package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for GofpFreelancer persistence operations.
 */
public interface GofpFreelancerRepository {

    Mono<GofpFreelancer> save(GofpFreelancer freelancer);

    Mono<GofpFreelancer> findById(UUID id);

    Mono<GofpFreelancer> findByCoreFreelancerId(UUID coreFreelancerId);

    Mono<GofpFreelancer> findByCoreUserId(UUID coreUserId);

    Flux<GofpFreelancer> findAllByStatus(FreelancerStatus status);

    Flux<GofpFreelancer> findAllByIsActive(Boolean isActive);

    Flux<GofpFreelancer> findAllByStatusAndIsActive(FreelancerStatus status, Boolean isActive);

    Flux<GofpFreelancer> findAll();

    Mono<Void> deleteById(UUID id);
}
