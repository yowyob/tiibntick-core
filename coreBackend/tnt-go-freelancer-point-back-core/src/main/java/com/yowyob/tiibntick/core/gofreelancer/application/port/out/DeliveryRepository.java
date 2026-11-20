package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
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
     * <p>Joins deliveries → delivery_needs → packets and sums
     * {@code packets.length * packets.width * packets.height} for active
     * deliveries (PICKED_UP, IN_TRANSIT). Dimensions are assumed to be in metres.
     *
     * @param freelancerId the freelancer UUID
     * @return total occupied volume in m³; 0.0 when no active delivery
     */
    Mono<Double> sumOccupiedVolumeM3ByFreelancerId(UUID freelancerId);
}
