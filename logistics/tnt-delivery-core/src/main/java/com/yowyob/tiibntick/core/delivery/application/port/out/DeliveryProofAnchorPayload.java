package com.yowyob.tiibntick.core.delivery.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivery-owned payload for {@link DeliveryProofAnchorPort#anchor}.
 *
 * <p>Deliberately independent from any {@code tnt-trust-core} domain type — the
 * implementing adapter (in {@code tnt-trust-core}) maps this into its own
 * {@code DeliveryProofRecord}, keeping the hexagonal boundary between the two modules.
 *
 * @param photoHash     required — hash of the actual proof photo content, never a URL
 * @param signatureHash optional — hash of a captured recipient signature, if any
 * @param gpsLat        required — latitude at proof capture time
 * @param gpsLng        required — longitude at proof capture time
 * @author MANFOUO Braun
 */
public record DeliveryProofAnchorPayload(
        UUID tenantId,
        UUID deliveryId,
        UUID packageId,
        UUID actorId,
        String photoHash,
        String signatureHash,
        double gpsLat,
        double gpsLng,
        Instant confirmedAt) {
}
