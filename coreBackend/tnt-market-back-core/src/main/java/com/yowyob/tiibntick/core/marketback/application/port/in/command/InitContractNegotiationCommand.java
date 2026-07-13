package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Command — initiate a merchant contract negotiation.
 * @author MANFOUO Braun
 */
public record InitContractNegotiationCommand(
        @NotNull String tenantId,
        @NotNull UUID merchantId,
        @NotNull UUID providerId,
        @NotNull UUID listingId,
        double baseDiscountPct,
        int minMonthlyOrders,
        int maxMonthlyOrders,
        int paymentTermDays,
        String specialConditions,
        LocalDate startDate,
        LocalDate endDate,
        boolean renewalOption,
        List<VolumeTierDto> volumeTiers
) {
    public record VolumeTierDto(int minOrders, int maxOrders, double discountPct, long flatRateXaf) {}
}
