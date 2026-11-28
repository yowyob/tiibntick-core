package com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request/response bodies of the delivery payment endpoints. */
public final class DeliveryPaymentDtos {

    private DeliveryPaymentDtos() {
    }

    public record PaymentChallengeResponse(UUID deliveryId, BigDecimal amount, String currency,
                                           String challengeToken, Long expiresInSeconds,
                                           String codePreview) {
    }

    public record ConfirmPaymentRequest(@jakarta.validation.constraints.NotBlank String challengeToken,
                                        @jakarta.validation.constraints.NotBlank String code) {
    }

    /**
     * @param status        PAID (released to the courier) | CASH_COLLECTED | UNPAID | NOT_SETTLED (nothing yet), or
     *                      the escrow state while the money is held: HELD | FROZEN | REFUNDED (lot L5)
     * @param payable       true when the client can start a payment now (no escrow, no settlement, priced, not
     *                      cancelled, not cash)
     * @param escrowStatus  HELD | FROZEN | RELEASED | REFUNDED, absent without escrow (lot L5)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentStatusResponse(UUID deliveryId, String status, BigDecimal amount, String currency,
                                        String method, String commissionStatus, BigDecimal commissionAmount,
                                        boolean payable, Instant paidAt, String escrowStatus) {
    }

    // ── Lot L5: paid selection (spec §4.1) ────────────────────────────────────────────────────────

    /** @param paymentMode ESCROW | CASH; absent → the announcement's payment method (CASH, else ESCROW) */
    public record SelectionChallengeRequest(String paymentMode) {
    }

    /**
     * The payment intent of a selection. ESCROW: the Kernel sent the client a code ({@code challengeToken},
     * {@code expiresInSeconds}, {@code codePreview}); CASH: no code, the confirmation opens the cash collection.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SelectionChallengeResponse(UUID intentId, UUID deliveryId, String paymentMode, BigDecimal amount,
                                             String currency, BigDecimal platformShare, BigDecimal courierShare,
                                             String challengeToken, Long expiresInSeconds, String codePreview,
                                             Instant expiresAt, BigDecimal guaranteeAmount,
                                             String guaranteeChallengeToken, Long guaranteeExpiresInSeconds,
                                             String guaranteeCodePreview) {

        /** Without a return guarantee (delivery not to a relay hub, CASH, FUND). */
        public SelectionChallengeResponse(UUID intentId, UUID deliveryId, String paymentMode, BigDecimal amount,
                                          String currency, BigDecimal platformShare, BigDecimal courierShare,
                                          String challengeToken, Long expiresInSeconds, String codePreview,
                                          Instant expiresAt) {
            this(intentId, deliveryId, paymentMode, amount, currency, platformShare, courierShare, challengeToken,
                    expiresInSeconds, codePreview, expiresAt, null, null, null, null);
        }
    }

    /**
     * {@code challengeToken} and {@code code} are required for ESCROW, ignored for CASH and FUND;
     * {@code guaranteeChallengeToken} and {@code guaranteeCode} (the second code) are required when the challenge
     * returned a return guarantee (delivery to a relay hub, D-152).
     */
    public record SelectionConfirmRequest(@jakarta.validation.constraints.NotNull UUID intentId,
                                          String challengeToken, String code, String guaranteeChallengeToken,
                                          String guaranteeCode) {

        public SelectionConfirmRequest(UUID intentId, String challengeToken, String code) {
            this(intentId, challengeToken, code, null, null);
        }
    }

    /**
     * @param paymentStatus   escrow HELD, cash collection OPEN, or FUNDED (a return paid by the fund)
     * @param guaranteeStatus return guarantee HELD, {@code null} when there is none
     */
    public record SelectionConfirmResponse(UUID deliveryId, String paymentMode, String paymentStatus,
                                           Object announcement, String guaranteeStatus) {

        public SelectionConfirmResponse(UUID deliveryId, String paymentMode, String paymentStatus,
                                        Object announcement) {
            this(deliveryId, paymentMode, paymentStatus, announcement, null);
        }
    }

    // ── Lot L5: cash collected by the courier (spec §4.2 K4) ──────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CashCollectionView(UUID deliveryId, String status, BigDecimal expectedAmount,
                                     BigDecimal collectedAmount, BigDecimal amountToRemit, String currency) {
    }
}
