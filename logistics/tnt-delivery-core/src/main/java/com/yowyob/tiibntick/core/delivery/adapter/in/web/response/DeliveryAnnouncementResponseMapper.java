package com.yowyob.tiibntick.core.delivery.adapter.in.web.response;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;

import java.util.Collections;
import java.util.List;

/**
 * Mapper from {@code DeliveryAnnouncement} domain aggregate to HTTP response DTO.
 *
 * @author MANFOUO Braun
 */
public final class DeliveryAnnouncementResponseMapper {

    private DeliveryAnnouncementResponseMapper() {}

    public static DeliveryAnnouncementResponse toResponse(DeliveryAnnouncement a) {
        String pickupDisplay = a.getPickupAddress() != null
                ? a.getPickupAddress().toDisplayString() : "";
        String delivDisplay  = a.getDeliveryAddress() != null
                ? a.getDeliveryAddress().toDisplayString() : "";

        List<DeliveryAnnouncementResponse.ResponseSummary> responseSummaries =
                a.getResponses() != null
                        ? a.getResponses().stream()
                                .map(r -> new DeliveryAnnouncementResponse.ResponseSummary(
                                        r.getId(),
                                        r.getDeliveryPersonId(),
                                        r.getEstimatedArrivalTime(),
                                        r.getNote(),
                                        r.getStatus(),
                                        r.getCreatedAt()))
                                .toList()
                        : Collections.emptyList();

        return new DeliveryAnnouncementResponse(
                a.getId(),
                a.getTenantId(),
                a.getClientId(),
                a.getTitle(),
                a.getDescription(),
                a.getOfferedAmount(),
                a.getCurrency(),
                a.getStatus(),
                a.getUrgency(),
                pickupDisplay,
                delivDisplay,
                a.getRecipient() != null ? a.getRecipient().name() : null,
                responseSummaries.size(),
                responseSummaries,
                a.getSelectedResponseId(),
                a.getCreatedDeliveryId(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
