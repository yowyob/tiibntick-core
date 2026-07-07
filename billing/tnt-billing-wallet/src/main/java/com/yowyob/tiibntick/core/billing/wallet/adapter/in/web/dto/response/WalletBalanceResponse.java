package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST response DTO for wallet balance queries.
 * @author MANFOUO Braun
 */
public record WalletBalanceResponse(
        UUID walletId,
        UUID userId,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        String currency,
        String status
) {}
