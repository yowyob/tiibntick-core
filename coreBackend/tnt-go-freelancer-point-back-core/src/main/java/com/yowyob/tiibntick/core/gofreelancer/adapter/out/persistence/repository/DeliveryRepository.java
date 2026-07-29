package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive R2DBC repository for Delivery entity.
 *
 * @author Kengfack Lagrange
 */
public interface DeliveryRepository extends ReactiveCrudRepository<Delivery, UUID> {

    Mono<Delivery> findByAnnouncementId(UUID announcementId);

    Flux<Delivery> findAllByFreelancerId(UUID freelancerId);

    Flux<Delivery> findAllByStatus(DeliveryStatus status);

    Mono<Delivery> findByDeliveryNeedId(UUID deliveryNeedId);

    /**
     * Returns deliveries where the packet is physically in the freelancer's vehicle
     * (statuses PICKED_UP and IN_TRANSIT). Used to compute occupied trunk volume.
     */
    @Query("SELECT * FROM deliveries WHERE freelancer_id = :freelancerId AND status IN ('PICKED_UP', 'IN_TRANSIT')")
    Flux<Delivery> findActiveByFreelancerId(UUID freelancerId);

    /**
     * Returns the total volume (m³) currently occupying a freelancer's trunk.
     *
     * <p>Joins deliveries → delivery_needs → packets and sums
     * {@code packets.length * packets.width * packets.height} for all active
     * deliveries (PICKED_UP, IN_TRANSIT). Packet dimensions are assumed to be in metres.
     *
     * @param freelancerId the freelancer UUID
     * @return total occupied volume in m³; 0.0 when no active delivery
     */
    @Query("""
            SELECT COALESCE(SUM(p.length * p.width * p.height), 0.0)
            FROM deliveries d
            JOIN delivery_needs dn ON dn.id = d.delivery_need_id
            JOIN packets p ON p.id = dn.packet_id
            WHERE d.freelancer_id = :freelancerId
              AND d.status IN ('PICKED_UP', 'IN_TRANSIT')
            """)
    Mono<Double> sumOccupiedVolumeM3ByFreelancerId(UUID freelancerId);
}
