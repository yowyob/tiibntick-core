package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.LogisticsPricingEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface R2dbcLogisticsPricingRepository
        extends ReactiveCrudRepository<LogisticsPricingEntity, UUID> {

    Mono<LogisticsPricingEntity> findByRelayHubId(UUID relayHubId);
}
