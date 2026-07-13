package com.yowyob.tiibntick.core.delivery.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound persistence port for the {@link Delivery} aggregate.
 *
 * @author MANFOUO Braun
 */
public interface DeliveryRepository {

    Mono<Delivery> save(Delivery delivery);

    Mono<Delivery> findById(UUID tenantId, UUID deliveryId);

    Mono<Delivery> findByTrackingCode(String trackingCode);

    Flux<Delivery> findBySenderId(UUID tenantId, UUID senderId);

    Flux<Delivery> findByDeliveryPersonId(UUID tenantId, UUID deliveryPersonId);

    Flux<Delivery> findByStatus(UUID tenantId, DeliveryStatus status);

    Flux<Delivery> findActiveByDeliveryPerson(UUID tenantId, UUID deliveryPersonId);

    /**
     * Counts deliveries assigned to a delivery person, without loading them — for
     * callers that only need a total (e.g. a profile summary), not the full history.
     */
    Mono<Long> countByDeliveryPersonId(UUID tenantId, UUID deliveryPersonId);

    /**
     * Counts deliveries assigned to a delivery person that are not in a terminal
     * status ({@link DeliveryStatus#isTerminal()}), without loading them.
     */
    Mono<Long> countNonTerminalByDeliveryPersonId(UUID tenantId, UUID deliveryPersonId);

    Mono<Void> delete(UUID tenantId, UUID deliveryId);

    /**
     * Finds a delivery by ID without tenant scoping.
     * Used by {@code MissionStatusPortAdapter} which receives only the deliveryId
     * from tnt-incident-core (no tenant context available in the port signature).
     *
     * @param deliveryId the delivery UUID (globally unique)
     * @return the delivery if found, empty otherwise
     */
    Mono<Delivery> findByIdNoTenant(UUID deliveryId);

    /**
     * Lists all deliveries currently paused by a specific incident.
     * Used by the incident consumer to reactivate deliveries on incident resolution.
     *
     * @param incidentId the blocking incident UUID
     * @return flux of deliveries paused by this incident
     */
    reactor.core.publisher.Flux<Delivery> findByPausedByIncidentId(UUID incidentId);

    /**
     * Lists all active deliveries assigned to a specific FreelancerOrg.
     * Used by tnt-billing-core and tnt-notify-core for FreelancerOrg mission tracking.
     *
     * @param freelancerOrgId the FreelancerOrg UUID
     * @return flux of deliveries assigned to this FreelancerOrg
     */
    Flux<Delivery> findByFreelancerOrgId(String freelancerOrgId);
}

