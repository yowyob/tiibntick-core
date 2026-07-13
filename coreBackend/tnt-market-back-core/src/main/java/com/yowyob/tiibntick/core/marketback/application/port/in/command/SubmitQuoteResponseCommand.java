package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Command — provider submits a response to a QuoteRequest.
 * @author MANFOUO Braun
 */
public record SubmitQuoteResponseCommand(
        @NotNull UUID providerId,
        @NotNull long proposedPriceXaf,
        LocalDateTime estimatedPickupAt,
        LocalDateTime estimatedDeliveryAt,
        double etaHours,
        String message,
        List<String> conditions,
        int validHours
) {}
