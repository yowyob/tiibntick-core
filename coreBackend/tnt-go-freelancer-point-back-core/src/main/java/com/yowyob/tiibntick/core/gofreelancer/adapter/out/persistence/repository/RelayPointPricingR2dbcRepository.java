package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointPricingPolicy;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive R2DBC repository for RelayPointPricingPolicy entity.
 */
public interface RelayPointPricingR2dbcRepository extends ReactiveCrudRepository<RelayPointPricingPolicy, UUID> {

    Mono<RelayPointPricingPolicy> findByLogisticsId(UUID logisticsId);
}
