package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface IGetAvailableDeliverersNearUseCase {

    Flux<DelivererProfile> findAvailableNear(UUID tenantId, double latitude, double longitude,
                                              double radiusKm, double minCapacityKg);
}
