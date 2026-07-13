package com.yowyob.tiibntick.core.gofp.application.port.in;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IDeliveryUseCase {

    /** Crée une livraison à partir d'une annonce assignée. */
    Mono<DeliveryEntity> createDelivery(UUID announcementId, UUID freelancerActorId);

    /** Le livreur a récupéré le colis → PICKED_UP. */
    Mono<DeliveryEntity> confirmPickup(UUID deliveryId);

    /** Le livreur est en route → IN_TRANSIT. */
    Mono<DeliveryEntity> startTransit(UUID deliveryId);

    /** Dépôt en point relais → AT_RELAY. */
    Mono<DeliveryEntity> depositAtRelay(UUID deliveryId, UUID relayHubId);

    /** Reprise depuis le point relais → IN_TRANSIT. */
    Mono<DeliveryEntity> resumeFromRelay(UUID deliveryId);

    /** Livraison complétée → DELIVERED (déclenche commission + évaluation). */
    Mono<DeliveryEntity> completeDelivery(UUID deliveryId, Double deliveryLat, Double deliveryLon);

    /** Livraison échouée → FAILED. */
    Mono<DeliveryEntity> failDelivery(UUID deliveryId, String reason);

    /** Annulation → CANCELLED. */
    Mono<DeliveryEntity> cancelDelivery(UUID deliveryId);

    /** Mise à jour position GPS livreur. */
    Mono<Void> updateLocation(UUID deliveryId, double lat, double lon);

    Mono<DeliveryEntity> findById(UUID id);
    Flux<DeliveryEntity> findByFreelancerActorId(UUID freelancerActorId);
}
