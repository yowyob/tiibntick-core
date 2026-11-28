package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for delivery persistence operations.
 */
public interface DeliveryRepository {

    Mono<Delivery> save(Delivery delivery);

    Mono<Delivery> findById(UUID id);

    Mono<Delivery> findByAnnouncementId(UUID announcementId);

    Flux<Delivery> findAllByFreelancerId(UUID freelancerId);

    Flux<Delivery> findAllByStatus(DeliveryStatus status);

    Mono<Delivery> findByDeliveryNeedId(UUID deliveryNeedId);

    /**
     * The delivery carrying this parcel, or empty: the core delivery whose parcel it is (whatever parcel the
     * core created), else the delivery of the need that registered it. A relay deposit is keyed on the parcel
     * id, so this is how a deposit finds its delivery.
     * <p><b>Reserved</b> for the retrieval of a parcel deposited at a relay point (a deposit is keyed on the
     * parcel id and has no tenant context). It crosses tenants: do not call it from anywhere else
     * ({@code ParcelLookupCallersArchitectureTest} fails the build otherwise).
     */
    Mono<Delivery> findByParcelId(UUID parcelId);

    Mono<Void> deleteById(UUID id);

    /**
     * Returns all active deliveries for a given freelancer that occupy trunk space.
     * "Active" means statuses where the packet is physically in the vehicle:
     * PICKED_UP and IN_TRANSIT.
     *
     * @param freelancerId the freelancer UUID
     * @return deliveries currently occupying the freelancer's trunk
     */
    Flux<Delivery> findActiveByFreelancerId(UUID freelancerId);

    /**
     * Number of deliveries of this courier that are not finished — neither delivered, cancelled nor
     * failed — whatever id space they were recorded under ({@code deliveries.freelancer_id} holds the
     * responder id of the elected answer: the core freelancer id, or the gofp id when it has none).
     * A courier with at least one is shown "busy" to clients and cannot be invited (lot C-52).
     */
    Mono<Long> countUnfinishedByFreelancerIds(java.util.Collection<UUID> freelancerIds);

    /**
     * Returns the total volume (m³) currently occupying a freelancer's trunk.
     *
     * <p>Sums the volume of the core parcels of the freelancer's active deliveries (PICKED_UP,
     * IN_TRANSIT), read from {@code tnt-delivery-core} (lot L1: there is no {@code packets} table any more).
     *
     * @param freelancerId the freelancer UUID
     * @return total occupied volume in m³; 0.0 when no active delivery
     */
    Mono<Double> sumOccupiedVolumeM3ByFreelancerId(UUID freelancerId);

    /**
     * Lot C-54 — deliveries of these courier ids that reached {@code DELIVERED}. Default 0 for the
     * implementations that predate it.
     */
    default Mono<Long> countDeliveredByFreelancerIds(java.util.Collection<UUID> freelancerIds) {
        return Mono.just(0L);
    }
}
