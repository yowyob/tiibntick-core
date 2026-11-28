package com.yowyob.tiibntick.core.delivery.domain.event;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementCreatorRole;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementOutcome;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Unmasked state of an announcement after a change (spec §3.6 {@code announcement.payload}). */
public record AnnouncementSnapshot(
        UUID announcementId,
        AnnouncementKind kind,
        UUID creatorId,
        AnnouncementCreatorRole creatorRole,
        UUID parcelId,
        AnnouncementStatus status,
        AnnouncementPricingMode pricingMode,
        BigDecimal proposedPrice,
        String currency,
        DeliveryAddress pickupAddress,
        DeliveryAddress deliveryAddress,
        UUID relayHubId,
        List<Response> responses,
        UUID selectedResponseId,
        UUID deliveryId,
        AnnouncementOutcome outcome,
        Instant publishedAt,
        Instant assignedAt,
        Instant closedAt) {

    public AnnouncementSnapshot {
        responses = responses == null ? List.of() : List.copyOf(responses);
    }

    /** One response, as published (courier id, price, status). */
    public record Response(UUID responseId, UUID courierId, BigDecimal price, ResponseStatus status,
                           Instant createdAt) {
    }
}
