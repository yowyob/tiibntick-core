package com.yowyob.tiibntick.core.marketback.application.port.in.result;

import com.yowyob.tiibntick.core.marketback.domain.model.OrderStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.PaymentMethod;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO — MarketOrder.
 * @author MANFOUO Braun
 */
public record MarketOrderResponse(
        UUID id,
        String tenantId,
        UUID clientId,
        UUID providerId,
        UUID listingId,
        UUID offerId,
        UUID quoteRequestId,
        OrderStatus status,
        String pickupCity,
        String deliveryCity,
        double weightKg,
        long totalAmountXaf,
        long discountAmountXaf,
        PaymentMethod paymentMethod,
        String transactionRef,
        UUID missionId,
        UUID invoiceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
