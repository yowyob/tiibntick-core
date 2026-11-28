package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
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
              AND status IN (:activeStatuses)
            ORDER BY created_at DESC
            """)
    Flux<DeliveryEntity> findActiveByTenantIdAndDeliveryPersonId(UUID tenantId, UUID dpId,
                                                                 Collection<String> activeStatuses);

    @Query("SELECT COUNT(*) FROM tnt_deliveries WHERE tenant_id = :tenantId AND delivery_person_id = :dpId")
    Mono<Long> countByTenantIdAndDeliveryPersonId(UUID tenantId, UUID dpId);

    @Query("SELECT COUNT(*) FROM tnt_deliveries WHERE tenant_id = :tenantId AND delivery_person_id = :dpId AND status = :status")
    Mono<Long> countByTenantIdAndDeliveryPersonIdAndStatus(UUID tenantId, UUID dpId, String status);

    @Query("""
            SELECT COUNT(*) FROM tnt_deliveries
            WHERE tenant_id = :tenantId
              AND delivery_person_id = :dpId
              AND status NOT IN (:terminalStatuses)
            """)
    Mono<Long> countNonTerminalByTenantIdAndDeliveryPersonId(UUID tenantId, UUID dpId,
                                                            Collection<String> terminalStatuses);

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

    // pauseByIncident / resumeFromIncident (raw SQL bypassing the state machine) removed by lot evt-l2:
    // every incident transition goes through Delivery (rows D13–D20) and emits its event.


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

    /**
     * Deliveries carrying this parcel, whatever their tenant, most recent first.
     * <p><b>Reserved</b> for the retrieval of a parcel deposited at a relay point (a deposit is keyed on the
     * parcel id and has no tenant context). It crosses tenants: do not call it from anywhere else
     * ({@code ParcelLookupCallersArchitectureTest} fails the build otherwise).
     */
    @Query("SELECT * FROM tnt_deliveries WHERE parcel_id = :parcelId ORDER BY created_at DESC, id")
    Flux<DeliveryEntity> findByParcelIdNoTenant(UUID parcelId);

    /** Deliveries waiting at their destination relay whose storage ended (row D12, spec §5.2). */
    @Query("""
            SELECT * FROM tnt_deliveries
            WHERE status = 'AT_RELAY_HUB'
              AND relay_storage_expires_at IS NOT NULL
              AND relay_storage_expires_at <= :now
            ORDER BY relay_storage_expires_at
            LIMIT :limit
            """)
    Flux<DeliveryEntity> findRelayStorageExpired(Instant now, int limit);

    /**
     * Deliveries the alert watch evaluates (lot L3): a courier is responsible and the parcel is not at a relay. Oldest
     * update first, so that a large backlog is covered over successive runs.
     */
    @Query("""
            SELECT * FROM tnt_deliveries
            WHERE status IN ('ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPT_FAILED',
                             'INCIDENT_REPORTED', 'PAUSED_BY_INCIDENT', 'INCIDENT_HANDOVER')
            ORDER BY updated_at
            LIMIT :limit
            """)
    Flux<DeliveryEntity> findWatchCandidates(int limit);

    /** Deliveries at their destination relay whose reminder is due and not sent yet (spec §5.2). */
    @Query("""
            SELECT * FROM tnt_deliveries
            WHERE status = 'AT_RELAY_HUB'
              AND storage_reminder_at IS NOT NULL
              AND storage_reminder_at <= :now
              AND storage_reminder_sent_at IS NULL
            ORDER BY storage_reminder_at
            LIMIT :limit
            """)
    Flux<DeliveryEntity> findRelayStorageReminderDue(Instant now, int limit);

    /** Non-terminal deliveries at a relay hub (custody of a revoked relay, D-129). */
    @Query("""
            SELECT * FROM tnt_deliveries
            WHERE status = 'AT_RELAY_HUB' AND relay_hub_id = :hubId
            ORDER BY created_at
            """)
    Flux<DeliveryEntity> findAtRelayHub(UUID hubId);
}
