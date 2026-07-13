package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayHubSubscriptionEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcRelayHubSubscriptionRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IRelayHubSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter : relie le port de domaine IRelayHubSubscriptionRepository
 * au repository R2DBC Spring Data.
 *
 * @author TiiBnTickTeam
 * @date 10/07/2026
 */
@Component
@RequiredArgsConstructor
public class RelayHubSubscriptionRepositoryAdapter implements IRelayHubSubscriptionRepository {

    private final R2dbcRelayHubSubscriptionRepository r2dbc;

    @Override public Mono<RelayHubSubscriptionEntity> save(RelayHubSubscriptionEntity e)     { return r2dbc.save(e); }
    @Override public Mono<RelayHubSubscriptionEntity> findById(UUID id)                       { return r2dbc.findById(id); }
    @Override public Mono<RelayHubSubscriptionEntity> findByRelayHubId(UUID relayHubId)       { return r2dbc.findByRelayHubId(relayHubId); }
}
