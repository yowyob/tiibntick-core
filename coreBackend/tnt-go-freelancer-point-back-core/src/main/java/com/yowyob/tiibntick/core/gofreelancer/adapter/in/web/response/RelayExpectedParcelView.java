package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;

/**
 * A parcel a relay point should receive: a delivery need that targets it and has not been deposited
 * there yet. Seen by the relay point owner only.
 */
public record RelayExpectedParcelView(
        UUID deliveryNeedId,
        UUID deliveryId,
        String title,
        String status,
        String recipientFirstName,
        String recipientLastName,
        Instant createdAt) {
}
