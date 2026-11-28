package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Command to record the terms agreed for a delivery: price and the pickup / hand-over windows.
 * Every term is optional; a null {@code amount} leaves the recorded price unchanged.
 */
public record RecordAgreedTermsCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        BigDecimal amount,
        String currency,
        Instant pickupWindowStart,
        Instant pickupWindowEnd,
        Instant deliveryWindowStart,
        Instant deliveryWindowEnd
) {
}
