package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Webhook callback payload from MTN MoMo Collections API.
 * Received at POST /billing/webhooks/mtn after USSD confirmation.
 *
 * @author MANFOUO Braun
 */
public record MtnCallbackRequest(
        @JsonProperty("externalId") String externalId,
        @JsonProperty("referenceId") String referenceId,
        @JsonProperty("status") String status,
        @JsonProperty("financialTransactionId") String financialTransactionId,
        @JsonProperty("reason") String reason
) {}
