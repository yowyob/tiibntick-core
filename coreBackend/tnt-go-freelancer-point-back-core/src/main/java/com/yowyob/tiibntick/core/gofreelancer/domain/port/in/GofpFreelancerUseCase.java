package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for GofpFreelancer operations.
 */
public interface GofpFreelancerUseCase {

    Mono<GofpFreelancer> createOrUpdate(GofpFreelancer freelancer);

    Mono<GofpFreelancer> findById(UUID id);

    Mono<GofpFreelancer> findByCoreFreelancerId(UUID coreFreelancerId);

    Mono<GofpFreelancer> findByCoreUserId(UUID coreUserId);

    Flux<GofpFreelancer> findAll();

    Flux<GofpFreelancer> findByStatus(FreelancerStatus status);

    Flux<GofpFreelancer> findActiveApproved();

    Mono<GofpFreelancer> updateStatus(UUID id, FreelancerStatus status);

    Mono<GofpFreelancer> setActive(UUID id, Boolean active);

    /** Updates GPS position. */
    Mono<GofpFreelancer> updateLocation(UUID coreFreelancerId, Float lat, Float lng);

    /** Records a failed delivery and updates metrics. */
    Mono<GofpFreelancer> recordFailedDelivery(UUID coreFreelancerId);

    /** Records a successful delivery and updates metrics. */
    Mono<GofpFreelancer> recordSuccessfulDelivery(UUID coreFreelancerId);

    Mono<Void> delete(UUID id);
}
