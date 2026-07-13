package com.yowyob.tiibntick.core.linkback.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryAnnouncementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RespondToAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.SelectAnnouncementResponseCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.linkback.application.port.in.ManageLinkBoardUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryLinkBoardUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Pure pass-through onto {@link DeliveryAnnouncementUseCase}/{@link DeliveryQueryUseCase}
 * — no board-specific logic is reimplemented, this class only exists so the Link BFF
 * has a single Link-facing entry point instead of calling tnt-delivery-core directly.
 *
 * @author Dilane PAFE
 */
@Service
@RequiredArgsConstructor
public class LinkBoardApplicationService implements ManageLinkBoardUseCase, QueryLinkBoardUseCase {

    private final DeliveryAnnouncementUseCase announcementUseCase;
    private final DeliveryQueryUseCase deliveryQueryUseCase;

    @Override
    public Mono<DeliveryAnnouncement> publish(CreateDeliveryAnnouncementCommand command) {
        return announcementUseCase.publishAnnouncement(command);
    }

    @Override
    public Mono<DeliveryAnnouncement> bid(RespondToAnnouncementCommand command) {
        return announcementUseCase.respondToAnnouncement(command);
    }

    @Override
    public Mono<DeliveryAnnouncement> elect(SelectAnnouncementResponseCommand command) {
        return announcementUseCase.selectResponse(command);
    }

    @Override
    public Mono<Void> cancel(UUID tenantId, UUID announcementId, UUID clientId) {
        return announcementUseCase.cancelAnnouncement(tenantId, announcementId, clientId);
    }

    @Override
    public Mono<DeliveryAnnouncement> findById(UUID tenantId, UUID announcementId) {
        return deliveryQueryUseCase.findAnnouncementById(tenantId, announcementId);
    }

    @Override
    public Flux<DeliveryAnnouncement> findOpen(UUID tenantId) {
        return deliveryQueryUseCase.findOpenAnnouncements(tenantId);
    }

    @Override
    public Flux<DeliveryAnnouncement> findMine(UUID tenantId, UUID clientId) {
        return deliveryQueryUseCase.findAnnouncementsByClient(tenantId, clientId);
    }
}
