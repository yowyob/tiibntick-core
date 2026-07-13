package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RespondToAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.SelectAnnouncementResponseCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Thin orchestration over {@code DeliveryAnnouncementUseCase} — the Link bulletin
 * board is exactly the existing delivery-announcement bounded context in
 * tnt-delivery-core, reused directly rather than reimplemented (publish/bid/elect
 * map 1:1 onto publishAnnouncement/respondToAnnouncement/selectResponse).
 */
public interface ManageLinkBoardUseCase {

    Mono<DeliveryAnnouncement> publish(CreateDeliveryAnnouncementCommand command);

    Mono<DeliveryAnnouncement> bid(RespondToAnnouncementCommand command);

    Mono<DeliveryAnnouncement> elect(SelectAnnouncementResponseCommand command);

    Mono<Void> cancel(UUID tenantId, UUID announcementId, UUID clientId);
}
