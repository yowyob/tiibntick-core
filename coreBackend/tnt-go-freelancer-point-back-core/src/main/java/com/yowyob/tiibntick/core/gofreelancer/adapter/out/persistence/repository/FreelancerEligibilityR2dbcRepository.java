package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerEligibility;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FreelancerEligibilityR2dbcRepository extends ReactiveCrudRepository<FreelancerEligibility, UUID> {

    Mono<FreelancerEligibility> findByDeliveryPersonId(UUID deliveryPersonId);
}
