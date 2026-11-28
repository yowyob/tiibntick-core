package com.yowyob.tiibntick.core.delivery.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound persistence port for the {@link Parcel} aggregate. */
public interface ParcelRepository {

    /** Inserts the parcel when its id is unknown, updates it otherwise. */
    Mono<Parcel> save(Parcel parcel);

    Mono<Parcel> findById(UUID parcelId);

    /** D-136: a parcel by its tracking code. */
    Mono<Parcel> findByTrackingCode(String trackingCode);
}
