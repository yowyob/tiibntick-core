package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.ParcelDetails;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port to the parcels owned by {@code tnt-delivery-core}.
 *
 * <p>gofp keeps no table of parcels: a parcel described by a customer is registered in the core, and its
 * id is the one that follows the goods from the delivery need to the announcement, the delivery and the
 * relay deposit.
 */
public interface ParcelPort {

    /**
     * Registers the parcel in the core and returns its id.
     * Missing weight or dimensions (and non-positive ones) take the neutral values 1 kg / 10 cm.
     */
    Mono<UUID> register(ParcelDetails details);

    /** The parcel with this id, or empty. */
    Mono<ParcelDetails> find(UUID parcelId);
}
