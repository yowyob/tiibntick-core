package com.yowyob.tiibntick.core.delivery.adapter.in.web.response;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;

/**
 * Mapper from {@code Delivery} domain aggregate to HTTP response DTOs.
 *
 * @author MANFOUO Braun
 */
public final class DeliveryResponseMapper {

    private DeliveryResponseMapper() {}

    public static DeliveryDetailResponse toDetail(Delivery d) {
        EtaEstimate eta = d.getCurrentEta();
        var estCost = d.getEstimatedCost();

        return new DeliveryDetailResponse(
                d.getId(),
                d.getTenantId(),
                d.getAnnouncementId(),
                d.getSenderId(),
                d.getDeliveryPersonId(),
                d.getStatus(),
                d.getUrgency(),
                d.getPickupAddress() != null ? d.getPickupAddress().toDisplayString() : "",
                d.getDeliveryAddress() != null ? d.getDeliveryAddress().toDisplayString() : "",
                d.getRecipient() != null ? d.getRecipient().name() : null,
                d.getRecipient() != null ? d.getRecipient().phoneNumber() : null,
                estCost != null ? estCost.total() : null,
                estCost != null ? estCost.currency() : null,
                d.getEstimatedDistanceKm(),
                eta != null ? eta.estimatedArrival() : null,
                eta != null ? eta.confidenceScore() : 0,
                eta != null ? eta.remainingMinutes() : 0,
                d.getScheduledPickupTime(),
                d.getEstimatedDeliveryTime(),
                d.getActualPickupTime(),
                d.getActualDeliveryTime(),
                d.getNotes(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    public static EtaResponse toEtaResponse(EtaEstimate eta) {
        return new EtaResponse(
                eta.estimatedArrival(),
                eta.lowerBound(),
                eta.upperBound(),
                eta.confidenceScore(),
                eta.remainingDistanceKm(),
                eta.remainingMinutes(),
                eta.isKalmanRefined());
    }
}
