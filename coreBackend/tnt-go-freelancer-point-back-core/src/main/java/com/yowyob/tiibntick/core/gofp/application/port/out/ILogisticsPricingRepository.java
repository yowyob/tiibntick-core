package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.LogisticsPricingEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ILogisticsPricingRepository {
    Mono<LogisticsPricingEntity> save(LogisticsPricingEntity entity);
    Mono<LogisticsPricingEntity> findByRelayHubId(UUID relayHubId);
}
