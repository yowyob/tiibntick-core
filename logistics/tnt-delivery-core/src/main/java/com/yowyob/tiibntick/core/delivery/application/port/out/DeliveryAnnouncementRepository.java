package com.yowyob.tiibntick.core.delivery.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

/**
 * Outbound persistence port for the {@link DeliveryAnnouncement} aggregate.
 *
 * @author MANFOUO Braun
 */
public interface DeliveryAnnouncementRepository {

    Mono<DeliveryAnnouncement> save(DeliveryAnnouncement announcement);

    Mono<DeliveryAnnouncement> findById(UUID tenantId, UUID announcementId);

    /** Tenant an announcement was published in (its id is globally unique), or empty if unknown. */
    default Mono<UUID> findTenantIdOf(UUID announcementId) {
        return Mono.empty();
    }

    Flux<DeliveryAnnouncement> findByClientId(UUID tenantId, UUID clientId);

    Flux<DeliveryAnnouncement> findByStatus(UUID tenantId, AnnouncementStatus status);

    Flux<DeliveryAnnouncement> findOpenAnnouncements(UUID tenantId);

    /** Annonces ouvertes visibles d'un livreur situé par {@code query} (filtre en base). */
    Flux<DeliveryAnnouncement> findOpenAnnouncementsNear(UUID tenantId,
            com.yowyob.tiibntick.core.delivery.application.port.in.query.NearbyAnnouncementQuery query);

    /**
     * Returns {@code true} if {@code freelancerId} already has a response on
     * {@code announcementId}, without loading the full announcement aggregate.
     */
    Mono<Boolean> hasResponse(UUID announcementId, UUID freelancerId);

    /** The announcement published for {@code (originType, originId)}, or empty. */
    Mono<DeliveryAnnouncement> findByOrigin(UUID tenantId, String originType, UUID originId);

    /** The announcements published for any of the given origins of {@code originType}. */
    Flux<DeliveryAnnouncement> findByOrigins(UUID tenantId, String originType, Collection<UUID> originIds);

    /** Announcements with a response from one of the given delivery persons, most recent first. */
    Flux<DeliveryAnnouncement> findRespondedBy(UUID tenantId, Collection<UUID> deliveryPersonIds);
}
