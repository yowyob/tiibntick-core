package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.FreelancerPricingRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerPricingR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerPricingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Bridges {@link FreelancerPricingRepository} to R2DBC.
 *
 * @author MANFOUO BRAUN
 */
@Component
@RequiredArgsConstructor
public class FreelancerPricingRepositoryAdapter implements FreelancerPricingRepository {

    private final FreelancerPricingR2dbcRepository r2dbcRepository;

    @Override
    public Mono<FreelancerPricingPolicy> findByDeliveryPersonId(UUID deliveryPersonId) {
        return r2dbcRepository.findByDeliveryPersonId(deliveryPersonId);
    }

    @Override
    public Mono<FreelancerPricingPolicy> save(FreelancerPricingPolicy policy) {
        return r2dbcRepository.save(policy);
    }
}
