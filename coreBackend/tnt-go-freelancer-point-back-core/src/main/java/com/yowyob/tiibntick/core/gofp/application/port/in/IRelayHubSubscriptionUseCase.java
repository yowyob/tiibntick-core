package com.yowyob.tiibntick.core.gofp.application.port.in;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayHubSubscriptionEntity;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.RelayHubSubscriptionType;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port entrant pour la gestion des abonnements des points relais.
 *
 * @author TiiBnTickTeam
 * @date 10/07/2026
 */
public interface IRelayHubSubscriptionUseCase {

    /**
     * Souscrit ou met à niveau le plan d'abonnement d'un point relais.
     * Si un abonnement existe déjà, il est mis à jour (upsert).
     *
     * @param relayHubId    UUID du point relais
     * @param type          plan souhaité (FREE / STANDARD / PREMIUM)
     * @param paymentMethod méthode de paiement (null pour FREE)
     */
    Mono<RelayHubSubscriptionEntity> subscribe(UUID relayHubId,
                                                RelayHubSubscriptionType type,
                                                String paymentMethod);

    /**
     * Annule l'abonnement d'un point relais (statut → CANCELLED).
     */
    Mono<RelayHubSubscriptionEntity> cancel(UUID relayHubId);

    /**
     * Retourne l'abonnement actif d'un point relais.
     */
    Mono<RelayHubSubscriptionEntity> findByRelayHubId(UUID relayHubId);

    /**
     * Vérifie si le point relais peut accepter un nouveau colis
     * selon son plan et sa capacité actuelle.
     */
    Mono<Boolean> canAcceptPacket(UUID relayHubId);

    /**
     * Incrémente le compteur de colis stockés après acceptation d'un dépôt.
     *
     * @param relayHubId UUID du point relais
     */
    Mono<RelayHubSubscriptionEntity> incrementPacketsUsed(UUID relayHubId);

    /**
     * Décrémente le compteur de colis stockés après récupération d'un colis.
     *
     * @param relayHubId UUID du point relais
     */
    Mono<RelayHubSubscriptionEntity> decrementPacketsUsed(UUID relayHubId);
}
