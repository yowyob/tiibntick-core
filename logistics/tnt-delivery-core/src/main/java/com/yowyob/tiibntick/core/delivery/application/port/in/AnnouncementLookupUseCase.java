package com.yowyob.tiibntick.core.delivery.application.port.in;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

/**
 * Inbound query port: announcements looked up by the request they publish (origin reference) or by
 * the delivery person who answered them. Complements {@link DeliveryQueryUseCase}.
 *
 * @author MANFOUO Braun
 */
public interface AnnouncementLookupUseCase {

    /** The announcement published for the given origin, or empty. */
    Mono<DeliveryAnnouncement> findAnnouncementByOrigin(UUID tenantId, String originType, UUID originId);

    /** Batch variant: the announcements published for any of the given origins (one query). */
    Flux<DeliveryAnnouncement> findAnnouncementsByOrigins(UUID tenantId, String originType,
                                                          Collection<UUID> originIds);

    /** Announcements on which one of the given delivery persons has a response, most recent first. */
    Flux<DeliveryAnnouncement> findAnnouncementsRespondedBy(UUID tenantId,
                                                            Collection<UUID> deliveryPersonIds);
}
