package com.yowyob.tiibntick.core.delivery.application.port.in;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound query port providing read access to delivery and announcement data.
 *
 * @author MANFOUO Braun
 */
public interface DeliveryQueryUseCase {

    /**
     * Finds a delivery by its UUID.
     */
    Mono<Delivery> findDeliveryById(UUID tenantId, UUID deliveryId);

    /** Tenant a delivery lives in (its id is globally unique), or empty if unknown — lot C-56. */
    default Mono<UUID> findDeliveryTenantId(UUID deliveryId) {
        return Mono.empty();
    }

    /**
     * Finds a delivery by its UUID whatever its tenant — for callers that hold the id of a delivery
     * created elsewhere and have no tenant context (background consumers). Empty when the delivery
     * does not exist (unlike {@link #findDeliveryById}, which fails).
     */
    Mono<Delivery> findDeliveryByIdAnyTenant(UUID deliveryId);

    /**
     * The deliveries that carry this parcel, whatever their tenant, most recent first (empty when none).
     * <p><b>Reserved</b> for the retrieval of a parcel deposited at a relay point (a deposit is keyed on the
     * parcel id and has no tenant context). It crosses tenants: do not call it from anywhere else
     * ({@code ParcelLookupCallersArchitectureTest} fails the build otherwise).
     */
    Flux<Delivery> findDeliveriesByParcelIdAnyTenant(UUID parcelId);

    /**
     * Finds a delivery by its tracking code.
     */
    Mono<Delivery> findByTrackingCode(String trackingCode);

    /** D-136: every delivery of the parcel carrying the code, oldest first (empty when unknown). */
    reactor.core.publisher.Flux<Delivery> findHistoryByTrackingCode(String trackingCode);

    /**
     * Lists all deliveries for a sender.
     */
    Flux<Delivery> findDeliveriesBySender(UUID tenantId, UUID senderId);

    /**
     * Lists all deliveries assigned to a delivery person.
     */
    Flux<Delivery> findDeliveriesByDeliveryPerson(UUID tenantId, UUID deliveryPersonId);

    /**
     * Counts deliveries assigned to a delivery person, without loading them — for
     * callers that only need a total (e.g. a profile summary), not the full history.
     */
    Mono<Long> countDeliveriesByDeliveryPerson(UUID tenantId, UUID deliveryPersonId);

    /**
     * Counts deliveries assigned to a delivery person that are not yet in a
     * terminal status ({@link DeliveryStatus#isTerminal()}), without loading them.
     */
    Mono<Long> countNonTerminalDeliveriesByDeliveryPerson(UUID tenantId, UUID deliveryPersonId);

    /**
     * Counts deliveries of a delivery person that are in the given status, without loading them
     * (e.g. how many a courier has delivered).
     */
    Mono<Long> countDeliveriesByDeliveryPersonAndStatus(UUID tenantId, UUID deliveryPersonId, DeliveryStatus status);

    /**
     * Lists deliveries by status for a given tenant.
     */
    Flux<Delivery> findDeliveriesByStatus(UUID tenantId, DeliveryStatus status);

    /**
     * Finds all announcements published by a client.
     */
    Flux<DeliveryAnnouncement> findAnnouncementsByClient(UUID tenantId, UUID clientId);

    /**
     * Finds an announcement by its UUID.
     */
    Mono<DeliveryAnnouncement> findAnnouncementById(UUID tenantId, UUID announcementId);

    /**
     * Tenant an announcement was published in, or empty if unknown. Lets a courier of another
     * tenant answer it (the Go marketplace is not split by tenant).
     */
    default Mono<UUID> findAnnouncementTenantId(UUID announcementId) {
        return Mono.empty();
    }

    /**
     * Returns all open (PUBLISHED or IN_NEGOTIATION) announcements for a tenant zone.
     */
    Flux<DeliveryAnnouncement> findOpenAnnouncements(UUID tenantId);

    /**
     * Annonces ouvertes visibles d'un livreur : dans le rayon de sa position, repli
     * même ville quand une position manque ; s'il n'a ni position ni ville, toutes les annonces
     * ouvertes (on ne lui cache pas le marché par manque de donnée).
     */
    Flux<DeliveryAnnouncement> findOpenAnnouncementsNear(UUID tenantId,
            com.yowyob.tiibntick.core.delivery.application.port.in.query.NearbyAnnouncementQuery query);

    /**
     * Returns {@code true} if {@code freelancerId} already has a response on the
     * given announcement, without loading the full announcement aggregate.
     * The {@code announcementId} is a globally-unique UUID PK, so tenant-scoping
     * adds no practical security — the parameter was removed to match what the
     * persistence layer actually does.
     */
    Mono<Boolean> hasFreelancerResponded(UUID announcementId, UUID freelancerId);

    /**
     * Lists all deliveries assigned to a specific FreelancerOrg ().
     *
     * @param freelancerOrgId the FreelancerOrg UUID
     * @return flux of deliveries for this FreelancerOrg
     */
    Flux<Delivery> listByFreelancerOrgId(String freelancerOrgId);

    /**
     * Resolves the kernel {@code actorId} for a registered delivery person.
     *
     * <p>The delivery person's profile ID ({@code tnt_delivery_persons.id}) and
     * their kernel actor ID ({@code tnt_delivery_persons.actor_id}) are two different
     * UUIDs for the same person. Payments and settlements are keyed on the {@code actor_id}
     * (the Kernel wallet owner), so callers must use the resolved {@code actorId}, not the profile ID.
     *
     * @param tenantId         the tenant scope
     * @param deliveryPersonId the profile ID from {@code tnt_delivery_persons.id}
     * @return the {@code actorId}, or an error if the delivery person is not found
     */
    Mono<UUID> resolveActorIdForDeliveryPerson(UUID tenantId, UUID deliveryPersonId);
}
