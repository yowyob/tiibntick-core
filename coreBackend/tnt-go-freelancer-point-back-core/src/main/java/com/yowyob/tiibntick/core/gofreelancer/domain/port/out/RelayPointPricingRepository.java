package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointPricingPolicy;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for retrieving relay point pricing policies.
 */
public interface RelayPointPricingRepository {
    
    /**
     * Retrieves the pricing policy for a given relay point.
     * @param logisticsId The UUID of the relay point.
     * @return A Mono emitting the pricing policy, or empty if not found.
     */
    Mono<RelayPointPricingPolicy> findByLogisticsId(UUID logisticsId);
}
