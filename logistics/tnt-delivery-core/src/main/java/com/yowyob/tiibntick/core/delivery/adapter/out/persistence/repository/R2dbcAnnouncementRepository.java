package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryAnnouncementEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@code DeliveryAnnouncementEntity}.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcAnnouncementRepository extends ReactiveCrudRepository<DeliveryAnnouncementEntity, UUID> {

    @Query("SELECT * FROM tnt_delivery_announcements WHERE tenant_id = :tenantId AND id = :id")
    Mono<DeliveryAnnouncementEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT * FROM tnt_delivery_announcements WHERE tenant_id = :tenantId AND client_id = :clientId ORDER BY created_at DESC")
    Flux<DeliveryAnnouncementEntity> findByTenantIdAndClientId(UUID tenantId, UUID clientId);

    @Query("SELECT * FROM tnt_delivery_announcements WHERE tenant_id = :tenantId AND status = :status ORDER BY created_at DESC")
    Flux<DeliveryAnnouncementEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    @Query("""
            SELECT * FROM tnt_delivery_announcements
            WHERE tenant_id = :tenantId
              AND status IN ('PUBLISHED','IN_NEGOTIATION')
            ORDER BY created_at DESC
            """)
    Flux<DeliveryAnnouncementEntity> findOpenByTenantId(UUID tenantId);
}
