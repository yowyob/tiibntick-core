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
     * Lists all deliveries assigned to a specific FreelancerOrg ().
     *
     * @param freelancerOrgId the FreelancerOrg UUID
     * @return flux of deliveries for this FreelancerOrg
     */
    Flux<Delivery> listByFreelancerOrgId(String freelancerOrgId);
}
