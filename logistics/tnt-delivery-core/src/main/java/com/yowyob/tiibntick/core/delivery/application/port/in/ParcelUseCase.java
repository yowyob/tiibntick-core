package com.yowyob.tiibntick.core.delivery.application.port.in;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.RegisterParcelCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port to register and read {@link Parcel}s independently of an announcement or a delivery.
 *
 * <p>An announcement and the delivery created from it share one parcel; a caller that describes the
 * goods before publishing registers the parcel here and keeps its id, so that the same id follows the
 * goods from the request through the announcement and the delivery to a relay deposit.
 */
public interface ParcelUseCase {

    /** Creates and persists a parcel; the returned aggregate carries the id to keep. */
    Mono<Parcel> registerParcel(RegisterParcelCommand command);

    /** The parcel with this id, or empty. */
    Mono<Parcel> findParcelById(UUID parcelId);
}
