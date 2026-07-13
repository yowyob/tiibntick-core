package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface QueryLinkBoardUseCase {

    Mono<DeliveryAnnouncement> findById(UUID tenantId, UUID announcementId);

    Flux<DeliveryAnnouncement> findOpen(UUID tenantId);

    Flux<DeliveryAnnouncement> findMine(UUID tenantId, UUID clientId);
}
