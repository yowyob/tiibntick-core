package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayHubSubscriptionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC Spring Data repository pour RelayHubSubscriptionEntity.
 *
 * @author TiiBnTickTeam
 * @date 10/07/2026
 */
@Repository
public interface R2dbcRelayHubSubscriptionRepository
        extends ReactiveCrudRepository<RelayHubSubscriptionEntity, UUID> {

    /**
     * Finds the subscription for a given relay hub.
     *
     * @param relayHubId UUID du point relais
     * @return l'abonnement, ou vide si aucun n'existe
     */
    Mono<RelayHubSubscriptionEntity> findByRelayHubId(UUID relayHubId);
}
