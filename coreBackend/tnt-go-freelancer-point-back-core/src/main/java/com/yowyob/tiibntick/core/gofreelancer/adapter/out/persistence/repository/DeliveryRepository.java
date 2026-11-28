package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive R2DBC repository for the gofp delivery extension rows.
 *
 * <p>Only key lookups live here. Every question about the delivery's own facts (status, assigned
 * delivery person, agreed price, windows) is answered by {@code tnt-delivery-core}, see
 * {@code GofpDeliveryRepositoryAdapter}.
 *
 * @author Kengfack Lagrange
 */
public interface DeliveryRepository extends ReactiveCrudRepository<Delivery, UUID> {

    Mono<Delivery> findByAnnouncementId(UUID announcementId);

    Mono<Delivery> findByDeliveryNeedId(UUID deliveryNeedId);

    /**
     * The delivery of the delivery need that registered this parcel ({@code delivery_needs.parcel_id}
     * holds the parcel id), if one was created.
     */
    @Query("""
            SELECT d.* FROM deliveries d
            JOIN delivery_needs n ON n.id = d.delivery_need_id
            WHERE n.parcel_id = :parcelId
            ORDER BY d.id
            LIMIT 1
            """)
    Mono<Delivery> findByParcelId(UUID parcelId);
}
