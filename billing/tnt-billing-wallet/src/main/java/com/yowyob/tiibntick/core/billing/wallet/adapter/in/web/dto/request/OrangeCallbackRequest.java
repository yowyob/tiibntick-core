package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Webhook callback payload from Orange Money Cameroon.
 * @author MANFOUO Braun
 */
public record OrangeCallbackRequest(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("status") String status,
        @JsonProperty("txnid") String transactionId,
        @JsonProperty("message") String message
) {}
