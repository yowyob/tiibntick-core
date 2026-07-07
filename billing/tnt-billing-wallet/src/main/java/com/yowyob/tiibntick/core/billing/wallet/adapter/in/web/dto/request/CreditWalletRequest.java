package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * REST request DTO to credit a wallet (admin/top-up operations).
 * @author MANFOUO Braun
 */
public record CreditWalletRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String referenceId,
        String description
) {}
