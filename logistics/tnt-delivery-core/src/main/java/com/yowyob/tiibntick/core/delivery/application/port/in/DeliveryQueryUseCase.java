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

    /**
     * Finds a delivery by its tracking code.
     */
    Mono<Delivery> findByTrackingCode(String trackingCode);

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
     * Returns all open (PUBLISHED or IN_NEGOTIATION) announcements for a tenant zone.
     */
    Flux<DeliveryAnnouncement> findOpenAnnouncements(UUID tenantId);

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
     * UUIDs for the same person. Wallet ownership is keyed on {@code actor_id}, so
     * callers that build {@link com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.DebitWalletCommand}
     * or {@link com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.SplitMissionRevenueCommand}
     * must use the resolved {@code actorId}, not the profile ID.
     *
     * @param tenantId         the tenant scope
     * @param deliveryPersonId the profile ID from {@code tnt_delivery_persons.id}
     * @return the {@code actorId}, or an error if the delivery person is not found
     */
    Mono<UUID> resolveActorIdForDeliveryPerson(UUID tenantId, UUID deliveryPersonId);
}
