package com.yowyob.tiibntick.core.gofp.application.port.in;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.SubscriptionEntity;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.SubscriptionType;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ISubscriptionUseCase {

    /** Crée ou met à jour le plan d'abonnement d'un livreur. */
    Mono<SubscriptionEntity> subscribe(UUID freelancerActorId, SubscriptionType type, String paymentMethod);

    /** Annule l'abonnement. */
    Mono<SubscriptionEntity> cancel(UUID freelancerActorId);

    /** Réinitialise le compteur mensuel (appelé par le cron). */
    Mono<SubscriptionEntity> resetMonthlyQuota(UUID freelancerActorId);

    Mono<SubscriptionEntity> findByFreelancerActorId(UUID freelancerActorId);

    /** Vérifie si le livreur peut encore accepter des livraisons ce mois-ci. */
    Mono<Boolean> canAcceptDelivery(UUID freelancerActorId);
}
