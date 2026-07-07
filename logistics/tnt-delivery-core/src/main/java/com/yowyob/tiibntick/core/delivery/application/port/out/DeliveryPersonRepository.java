package com.yowyob.tiibntick.core.delivery.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound persistence port for the {@link DeliveryPerson} aggregate.
 *
 * @author MANFOUO Braun
 */
public interface DeliveryPersonRepository {

    Mono<DeliveryPerson> save(DeliveryPerson deliveryPerson);

    Mono<DeliveryPerson> findById(UUID tenantId, UUID deliveryPersonId);

    Mono<DeliveryPerson> findByActorId(UUID tenantId, UUID actorId);

    Flux<DeliveryPerson> findAvailableNear(UUID tenantId, GeoCoordinates center, double radiusKm);

    Flux<DeliveryPerson> findByStatus(UUID tenantId, DeliveryPersonStatus status);
}
