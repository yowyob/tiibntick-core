package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerPricingPolicy;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive R2DBC repository for {@link FreelancerPricingPolicy}.
 *
 * @author MANFOUO BRAUN
 */
public interface FreelancerPricingR2dbcRepository
        extends ReactiveCrudRepository<FreelancerPricingPolicy, UUID> {

    Mono<FreelancerPricingPolicy> findByDeliveryPersonId(UUID deliveryPersonId);
}
