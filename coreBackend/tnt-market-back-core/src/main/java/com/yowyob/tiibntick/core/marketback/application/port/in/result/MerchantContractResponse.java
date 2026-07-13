package com.yowyob.tiibntick.core.marketback.application.port.in.result;

import com.yowyob.tiibntick.core.marketback.domain.model.ContractStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO — MerchantContract.
 * @author MANFOUO Braun
 */
public record MerchantContractResponse(
        UUID id,
        String tenantId,
        UUID merchantId,
        UUID providerId,
        UUID listingId,
        ContractStatus status,
        double baseDiscountPct,
        int minMonthlyOrders,
        int maxMonthlyOrders,
        LocalDate startDate,
        LocalDate endDate,
        int totalOrdersExecuted,
        long totalAmountXaf,
        LocalDateTime signedAt,
        LocalDateTime createdAt
) {}
