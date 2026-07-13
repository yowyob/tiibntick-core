package com.yowyob.tiibntick.core.agency.inbox.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.inbox.adapter.out.persistence.entity.NotificationEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AgencyNotificationR2dbcRepository extends ReactiveCrudRepository<NotificationEntity, UUID> {

    @Query("""
            SELECT * FROM agency_inbox.notifications
            WHERE agency_id = :agencyId AND tenant_id = :tenantId
            ORDER BY created_at DESC
            LIMIT :limit
            """)
    Flux<NotificationEntity> findByAgency(UUID agencyId, UUID tenantId, int limit);

    Mono<NotificationEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Modifying
    @Query("""
            UPDATE agency_inbox.notifications SET is_read = true
            WHERE agency_id = :agencyId AND tenant_id = :tenantId AND is_read = false
            """)
    Mono<Integer> markAllRead(UUID agencyId, UUID tenantId);
}
