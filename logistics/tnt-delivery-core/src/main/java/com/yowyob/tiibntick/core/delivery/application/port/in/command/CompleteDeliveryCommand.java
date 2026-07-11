package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to mark a delivery as successfully completed.
 *
 * <p>{@code photoHash}/{@code gpsLat}/{@code gpsLng} are optional: when all three are
 * present, the completion triggers a blockchain anchoring call via
 * {@code DeliveryProofAnchorPort}. When absent, the delivery still completes normally —
 * no proof is anchored (better than anchoring fabricated data).
 *
 * @author MANFOUO Braun
 */
public record CompleteDeliveryCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId,
        String proofPhotoUrl,
        String photoHash,
        String signatureHash,
        Double gpsLat,
        Double gpsLng
) {}
