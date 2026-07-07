package com.yowyob.tiibntick.core.delivery.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound persistence port for the {@link DeliveryAnnouncement} aggregate.
 *
 * @author MANFOUO Braun
 */
public interface DeliveryAnnouncementRepository {

    Mono<DeliveryAnnouncement> save(DeliveryAnnouncement announcement);

    Mono<DeliveryAnnouncement> findById(UUID tenantId, UUID announcementId);

    Flux<DeliveryAnnouncement> findByClientId(UUID tenantId, UUID clientId);

    Flux<DeliveryAnnouncement> findByStatus(UUID tenantId, AnnouncementStatus status);

    Flux<DeliveryAnnouncement> findOpenAnnouncements(UUID tenantId);
}
