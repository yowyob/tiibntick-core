package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST response DTO for WalletTransaction queries.
 * @author MANFOUO Braun
 */
public record WalletTransactionResponse(
        UUID transactionId,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String currency,
        String channel,
        String referenceId,
        String externalRef,
        String status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {}
