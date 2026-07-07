package com.yowyob.tiibntick.core.delivery.application.port.in;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RespondToAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.SelectAnnouncementResponseCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port (primary/driving) for the delivery announcement bounded context.
 *
 * <p>Covers the full TiiBnPick-style lifecycle:
 * publish → collect responses → select winner → trigger delivery creation.
 *
 * @author MANFOUO Braun
 */
public interface DeliveryAnnouncementUseCase {

    /**
     * Creates and publishes a new delivery announcement.
     *
     * @return persisted announcement in PUBLISHED status
     */
    Mono<DeliveryAnnouncement> publishAnnouncement(CreateDeliveryAnnouncementCommand command);

    /**
     * Registers a delivery person's response (bid) to an announcement.
     */
    Mono<DeliveryAnnouncement> respondToAnnouncement(RespondToAnnouncementCommand command);

    /**
     * Client selects a delivery person's response.
     * Side-effect: creates a {@link Delivery}.
     *
     * @return updated announcement in ASSIGNED status
     */
    Mono<DeliveryAnnouncement> selectResponse(SelectAnnouncementResponseCommand command);

    /**
     * Client cancels their announcement.
     */
    Mono<Void> cancelAnnouncement(UUID tenantId, UUID announcementId, UUID clientId);
}
