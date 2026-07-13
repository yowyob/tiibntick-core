package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayHubSubscriptionEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant pour la persistence des abonnements de points relais.
 *
 * @author TiiBnTickTeam
 * @date 10/07/2026
 */
public interface IRelayHubSubscriptionRepository {

    Mono<RelayHubSubscriptionEntity> save(RelayHubSubscriptionEntity entity);

    Mono<RelayHubSubscriptionEntity> findById(UUID id);

    /**
     * Trouve l'abonnement d'un point relais par son identifiant hub.
     *
     * @param relayHubId UUID du point relais
     */
    Mono<RelayHubSubscriptionEntity> findByRelayHubId(UUID relayHubId);
}
