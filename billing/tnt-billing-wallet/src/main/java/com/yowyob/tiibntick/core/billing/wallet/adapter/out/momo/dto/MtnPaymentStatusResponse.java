package com.yowyob.tiibntick.core.billing.wallet.adapter.out.momo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from MTN MoMo GET /collection/v1_0/requesttopay/{referenceId}.
 * @author MANFOUO Braun
 */
public record MtnPaymentStatusResponse(
        String status,
        @JsonProperty("financialTransactionId") String financialTransactionId,
        String reason
) {}
