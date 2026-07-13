package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayHubExtensionEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcRelayHubExtensionRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IRelayHubExtensionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RelayHubExtensionRepositoryAdapter implements IRelayHubExtensionRepository {

    private final R2dbcRelayHubExtensionRepository r2dbc;

    @Override public Mono<RelayHubExtensionEntity> save(RelayHubExtensionEntity e)    { return r2dbc.save(e); }
    @Override public Mono<RelayHubExtensionEntity> findByRelayHubId(UUID relayHubId)  { return r2dbc.findByRelayHubId(relayHubId); }
}
