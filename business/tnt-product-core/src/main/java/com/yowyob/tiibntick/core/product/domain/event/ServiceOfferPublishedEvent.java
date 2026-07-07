package com.yowyob.tiibntick.core.product.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ServiceOfferPublishedEvent(UUID offerId, UUID tenantId, UUID providerId, String offerName, Instant occurredAt) {
    public static ServiceOfferPublishedEvent of(UUID offerId, UUID tenantId, UUID providerId, String offerName) {
        return new ServiceOfferPublishedEvent(offerId, tenantId, providerId, offerName, Instant.now());
    }
}
