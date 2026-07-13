package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import reactor.core.publisher.Mono;

/**
 * Resolves a Link parcel by its tracking code. Reuses tnt-delivery-core's
 * {@code Delivery} aggregate directly rather than inventing a parallel domain
 * model — tnt-link-back-core's job is orchestration, not re-implementation.
 */
public interface TrackParcelUseCase {

    Mono<Delivery> trackByCode(String trackingCode);
}
