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

    /**
     * D-136: the delivery shown for a tracking code — the current (non-terminal) delivery of the parcel carrying the
     * code, else its latest delivery; for a code printed before D-136, the delivery that carried it.
     */
    Mono<Delivery> findByTrackingCode(String trackingCode);

    /** D-136: every delivery of the parcel carrying the code (outbound, return, redelivery), oldest first. */
    Flux<Delivery> findHistoryByTrackingCode(String trackingCode);

    /** Deliveries at their destination relay whose storage ended (spec §5.2), at most {@code limit}. */
    Flux<Delivery> findRelayStorageExpired(java.time.Instant now, int limit);

    /** Deliveries at their destination relay whose storage reminder is due (spec §5.2), at most {@code limit}. */
    Flux<Delivery> findRelayStorageReminderDue(java.time.Instant now, int limit);

    /** Deliveries the alert watch evaluates (lot L3: courier responsible, parcel not at a relay), at most {@code limit}. */
    Flux<Delivery> findWatchCandidates(int limit);

    /** Deliveries whose parcel is held at the relay hub (D-129: relay revoked). */
    Flux<Delivery> findAtRelayHub(UUID hubId);

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

    /**
     * Counts deliveries assigned to a delivery person that are in the given status, without loading them.
     */
    Mono<Long> countByDeliveryPersonIdAndStatus(UUID tenantId, UUID deliveryPersonId, DeliveryStatus status);

    Mono<Void> delete(UUID tenantId, UUID deliveryId);

    /**
     * Finds a delivery by ID without tenant scoping.
     * Used by {@code DeliveryStatusPortAdapter} which receives only the deliveryId
     * from tnt-incident-core (no tenant context available in the port signature).
     *
     * @param deliveryId the delivery UUID (globally unique)
     * @return the delivery if found, empty otherwise
     */
    Mono<Delivery> findByIdNoTenant(UUID deliveryId);

    /**
     * The deliveries carrying this parcel, whatever their tenant, most recent first.
     * <p><b>Reserved</b> for the retrieval of a parcel deposited at a relay point (a deposit is keyed on the
     * parcel id and has no tenant context). It crosses tenants: do not call it from anywhere else
     * ({@code ParcelLookupCallersArchitectureTest} fails the build otherwise).
     */
    Flux<Delivery> findByParcelIdNoTenant(UUID parcelId);

    /**
     * The deliveries carrying this parcel in one tenant, most recent first. A {@code null} tenant, or a delivery
     * without tenant, does not filter (tenantless parcel, decision D-150). Used by the dispute outcome, which knows the
     * dispute's tenant: unlike {@link #findByParcelIdNoTenant(UUID)}, it never crosses tenants.
     */
    Flux<Delivery> findByParcelId(UUID parcelId, UUID tenantId);

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

