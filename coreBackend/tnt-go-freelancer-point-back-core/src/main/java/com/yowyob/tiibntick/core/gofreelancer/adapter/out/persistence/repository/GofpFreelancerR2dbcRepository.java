package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive R2DBC repository for GofpFreelancer entity.
 */
public interface GofpFreelancerR2dbcRepository extends ReactiveCrudRepository<GofpFreelancer, UUID> {

    Mono<GofpFreelancer> findByCoreFreelancerId(UUID coreFreelancerId);

    Mono<GofpFreelancer> findByCoreUserId(UUID coreUserId);

    Flux<GofpFreelancer> findAllByStatus(FreelancerStatus status);

    Flux<GofpFreelancer> findAllByIsActive(Boolean isActive);

    Flux<GofpFreelancer> findAllByStatusAndIsActive(FreelancerStatus status, Boolean isActive);
}
