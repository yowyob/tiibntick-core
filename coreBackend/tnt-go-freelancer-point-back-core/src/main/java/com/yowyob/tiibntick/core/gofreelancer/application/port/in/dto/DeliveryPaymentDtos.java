package com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request/response bodies of the delivery payment endpoints. */
public final class DeliveryPaymentDtos {

    private DeliveryPaymentDtos() {
    }

    /** Returned to the delivery person only; the code is also stored on the delivery. */
    public record ReceiveCodeResponse(UUID deliveryId, String code, Instant expiresAt) {
    }

    public record PaymentChallengeResponse(UUID deliveryId, BigDecimal amount, String currency,
                                           String challengeToken, Long expiresInSeconds,
                                           String codePreview) {
    }

    public record ConfirmPaymentRequest(@jakarta.validation.constraints.NotBlank String challengeToken,
                                        @jakarta.validation.constraints.NotBlank String code) {
    }

    /**
     * @param status PAID | CASH_COLLECTED | UNPAID | NOT_SETTLED (no settlement recorded yet)
     * @param payable true when the client can start a payment now (a valid receive code exists)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentStatusResponse(UUID deliveryId, String status, BigDecimal amount, String currency,
                                        String method, String commissionStatus, BigDecimal commissionAmount,
                                        boolean payable, Instant paidAt) {
    }
}
