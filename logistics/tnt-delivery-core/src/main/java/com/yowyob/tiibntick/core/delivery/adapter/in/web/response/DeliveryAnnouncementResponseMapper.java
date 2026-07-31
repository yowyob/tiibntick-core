package com.yowyob.tiibntick.core.delivery.adapter.in.web.response;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Mapper from {@code DeliveryAnnouncement} domain aggregate to HTTP response DTO.
 *
 * @author MANFOUO Braun
 */
public final class DeliveryAnnouncementResponseMapper {

    private DeliveryAnnouncementResponseMapper() {}

    /** Full view for the owning client — includes all offers / proposed prices. */
    public static DeliveryAnnouncementResponse toResponse(DeliveryAnnouncement a) {
        return toResponse(a, null, true);
    }

    /**
     * Asymmetric view for a candidate freelancer: other freelancers' proposed prices
     * are redacted; the caller's own offer remains visible.
     */
    public static DeliveryAnnouncementResponse toCandidateResponse(
            DeliveryAnnouncement a, UUID viewerDeliveryPersonId) {
        return toResponse(a, viewerDeliveryPersonId, false);
    }

    private static DeliveryAnnouncementResponse toResponse(
            DeliveryAnnouncement a, UUID viewerDeliveryPersonId, boolean clientView) {
        String pickupDisplay = a.getPickupAddress() != null
                ? a.getPickupAddress().toDisplayString() : "";
        String delivDisplay  = a.getDeliveryAddress() != null
                ? a.getDeliveryAddress().toDisplayString() : "";

        List<DeliveryAnnouncementResponse.ResponseSummary> responseSummaries =
                a.getResponses() != null
                        ? a.getResponses().stream()
                                .map(r -> toSummary(r, viewerDeliveryPersonId, clientView))
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
                a.getPricingMode(),
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

    private static DeliveryAnnouncementResponse.ResponseSummary toSummary(
            AnnouncementResponse r, UUID viewerDeliveryPersonId, boolean clientView) {
        boolean revealPrice = clientView
                || (viewerDeliveryPersonId != null
                    && viewerDeliveryPersonId.equals(r.getDeliveryPersonId()));
        return new DeliveryAnnouncementResponse.ResponseSummary(
                r.getId(),
                r.getDeliveryPersonId(),
                r.getEstimatedArrivalTime(),
                r.getNote(),
                revealPrice ? r.getProposedPrice() : null,
                revealPrice ? r.getProposedCurrency() : null,
                r.getStatus(),
                r.getCreatedAt());
    }
}
