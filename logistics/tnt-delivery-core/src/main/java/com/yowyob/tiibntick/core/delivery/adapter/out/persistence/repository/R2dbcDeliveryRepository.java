package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@code DeliveryEntity}.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcDeliveryRepository extends ReactiveCrudRepository<DeliveryEntity, UUID> {

    @Query("SELECT * FROM tnt_deliveries WHERE tenant_id = :tenantId AND id = :id")
    Mono<DeliveryEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Mono<DeliveryEntity> findByTrackingCode(String trackingCode);

    @Query("SELECT * FROM tnt_deliveries WHERE tenant_id = :tenantId AND sender_id = :senderId ORDER BY created_at DESC")
    Flux<DeliveryEntity> findByTenantIdAndSenderId(UUID tenantId, UUID senderId);

    @Query("SELECT * FROM tnt_deliveries WHERE tenant_id = :tenantId AND delivery_person_id = :dpId ORDER BY created_at DESC")
    Flux<DeliveryEntity> findByTenantIdAndDeliveryPersonId(UUID tenantId, UUID dpId);

    @Query("SELECT * FROM tnt_deliveries WHERE tenant_id = :tenantId AND status = :status ORDER BY created_at DESC")
    Flux<DeliveryEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    @Query("""
            SELECT * FROM tnt_deliveries
            WHERE tenant_id = :tenantId
              AND delivery_person_id = :dpId
              AND status IN ('PICKED_UP','IN_TRANSIT','AT_RELAY_POINT')
            ORDER BY created_at DESC
            """)
    Flux<DeliveryEntity> findActiveByTenantIdAndDeliveryPersonId(UUID tenantId, UUID dpId);

    /**
     * Finds a delivery by ID without tenant scoping (for cross-module calls from tnt-incident-core).
     */
    @Query("SELECT * FROM tnt_deliveries WHERE id = :id LIMIT 1")
    Mono<DeliveryEntity> findById(UUID id);

    /**
     * Finds all deliveries currently paused by a specific incident.
     * Used by IncidentEventConsumer to reactivate deliveries on incident resolution.
     */
    @Query("SELECT * FROM tnt_deliveries WHERE paused_by_incident_id = :incidentId")
    Flux<DeliveryEntity> findByPausedByIncidentId(UUID incidentId);

    /**
     * Atomically updates the delivery for incident pause operation.
     */
    @Modifying
    @Query("""
            UPDATE tnt_deliveries
            SET status = 'PAUSED_BY_INCIDENT',
                previous_status_before_pause = status,
                paused_by_incident_id = :incidentId,
                updated_at = NOW()
            WHERE id = :deliveryId
              AND status NOT IN ('DELIVERED','CANCELLED','FAILED')
            """)
    Mono<Void> pauseByIncident(UUID deliveryId, UUID incidentId);

    /**
     * Atomically resumes a delivery from incident pause with optional new driver.
     */
    @Modifying
    @Query("""
            UPDATE tnt_deliveries
            SET status = previous_status_before_pause,
                delivery_person_id = COALESCE(:newDriverId, delivery_person_id),
                paused_by_incident_id = NULL,
                previous_status_before_pause = NULL,
                updated_at = NOW()
            WHERE id = :deliveryId
              AND status = 'PAUSED_BY_INCIDENT'
            """)
    Mono<Void> resumeFromIncident(UUID deliveryId, UUID newDriverId);

    /**
     * Finds all deliveries assigned to a specific FreelancerOrg ().
     * Used for FreelancerOrg mission tracking.
     */
    @Query("SELECT * FROM tnt_deliveries WHERE assigned_freelancer_org_id = :freelancerOrgId ORDER BY created_at DESC")
    Flux<DeliveryEntity> findByAssignedFreelancerOrgId(String freelancerOrgId);

    /**
     * Finds delivery by ID without tenant scoping — needed for cross-module Kafka events.
     */
    @Query("SELECT * FROM tnt_deliveries WHERE id = :deliveryId LIMIT 1")
    Mono<DeliveryEntity> findByIdNoTenant(UUID deliveryId);
}
