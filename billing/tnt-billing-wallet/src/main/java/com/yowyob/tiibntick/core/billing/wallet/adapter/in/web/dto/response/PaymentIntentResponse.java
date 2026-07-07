package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST response DTO for PaymentIntent creation.
 * @author MANFOUO Braun
 */
public record PaymentIntentResponse(
        UUID paymentIntentId,
        String invoiceId,
        BigDecimal amount,
        String currency,
        String channel,
        String status,
        String externalRef,
        String message,
        LocalDateTime expiresAt
) {}
