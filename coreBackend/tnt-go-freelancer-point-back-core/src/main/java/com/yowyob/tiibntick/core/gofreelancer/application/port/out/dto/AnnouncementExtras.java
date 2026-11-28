package com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Optional complements of an announcement, on the gofp side of the delivery-core port. Every component
 * is nullable ("not specified"); in an update, a null component means "unchanged".
 *
 * <p>{@code urgency} is the raw wire value ({@code STANDARD}, {@code EXPRESS}, {@code SAME_DAY},
 * {@code SCHEDULED}, or the tolerated legacy alias {@code NORMAL}); the adapter maps it.
 *
 * @author MANFOUO BRAUN
 */
public record AnnouncementExtras(
        String senderName,
        String senderPhone,
        String senderEmail,
        String recipientEmail,
        String urgency,
        String requiredVehicleType,
        String paymentMethod,
        String transportMethod,
        BigDecimal distanceKm,
        Integer estimatedDurationMin,
        UUID destinationHubId,
        String handoverSignatureRef,
        BigDecimal serviceFee,
        String originRefType,
        UUID originRefId) {

    /** Origin type used for the announcement that publishes a Go delivery need. */
    public static final String ORIGIN_DELIVERY_NEED = "DELIVERY_NEED";

    public static final AnnouncementExtras EMPTY = new AnnouncementExtras(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
}
