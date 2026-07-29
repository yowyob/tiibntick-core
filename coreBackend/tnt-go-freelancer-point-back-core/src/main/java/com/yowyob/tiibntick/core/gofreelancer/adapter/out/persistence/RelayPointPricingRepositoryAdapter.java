package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointPricingPolicy;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.RelayPointPricingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges RelayPointPricingRepository to the R2DBC Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class RelayPointPricingRepositoryAdapter implements RelayPointPricingRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.RelayPointPricingR2dbcRepository r2dbcRepository;

    @Override
    public Mono<RelayPointPricingPolicy> findByLogisticsId(UUID logisticsId) {
        return r2dbcRepository.findByLogisticsId(logisticsId);
    }
}
