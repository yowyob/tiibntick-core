package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayDepositEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcRelayDepositRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IRelayDepositRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RelayDepositRepositoryAdapter implements IRelayDepositRepository {

    private final R2dbcRelayDepositRepository r2dbc;

    @Override public Mono<RelayDepositEntity> save(RelayDepositEntity e)                     { return r2dbc.save(e); }
    @Override public Mono<RelayDepositEntity> findById(UUID id)                               { return r2dbc.findById(id); }
    @Override public Flux<RelayDepositEntity> findByRelayHubId(UUID relayHubId)               { return r2dbc.findByRelayHubId(relayHubId); }
    @Override public Flux<RelayDepositEntity> findByClientActorId(UUID clientActorId)         { return r2dbc.findByClientActorId(clientActorId); }
    @Override public Flux<RelayDepositEntity> findByPacketId(UUID packetId)                   { return r2dbc.findByPacketId(packetId); }
}
