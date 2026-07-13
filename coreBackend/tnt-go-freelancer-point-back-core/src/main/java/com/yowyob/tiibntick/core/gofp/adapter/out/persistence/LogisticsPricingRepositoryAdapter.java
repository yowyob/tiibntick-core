package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.LogisticsPricingEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcLogisticsPricingRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.ILogisticsPricingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LogisticsPricingRepositoryAdapter implements ILogisticsPricingRepository {

    private final R2dbcLogisticsPricingRepository r2dbc;

    @Override public Mono<LogisticsPricingEntity> save(LogisticsPricingEntity e)      { return r2dbc.save(e); }
    @Override public Mono<LogisticsPricingEntity> findByRelayHubId(UUID relayHubId)   { return r2dbc.findByRelayHubId(relayHubId); }
}
