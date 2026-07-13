package com.yowyob.tiibntick.core.gofp.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port de publication d'événements Kafka propres au module Market.
 */
public interface IGofpEventPublisher {

    /** Publie un événement "annonce publiée" — déclenche le matching. */
    Mono<Void> publishAnnouncementPublished(UUID announcementId, UUID clientActorId);

    /** Publie un événement "livraison complétée" — déclenche commission + évaluation. */
    Mono<Void> publishDeliveryCompleted(UUID deliveryId, UUID freelancerActorId, UUID clientActorId);

    /** Publie un événement "abonnement suspendu" (quota épuisé). */
    Mono<Void> publishSubscriptionSuspended(UUID subscriptionId, UUID freelancerActorId);
}
