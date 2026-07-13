package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkAlert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface QueryNetworkAlertsUseCase {

    Mono<NetworkAlert> findById(UUID tenantId, UUID alertId);

    /** Active alerts within {@code radiusKm} of {@code center} (haversine distance). */
    Flux<NetworkAlert> findActiveNearby(UUID tenantId, GeoPoint center, double radiusKm);
}
