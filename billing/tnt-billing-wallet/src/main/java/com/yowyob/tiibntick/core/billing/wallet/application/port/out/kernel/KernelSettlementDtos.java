package com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bodies of the Kernel {@code settlement-controller} ({@code /api/payments/escrows}, {@code /cash-collections},
 * {@code /compensation-fund}, {@code /compensations}), field for field from {@code docs/kernel-api/openapi.json}
 * (lot L5, spec §4). Unknown fields are ignored; {@code null} request fields are not sent.
 */
public final class KernelSettlementDtos {

    private KernelSettlementDtos() {
    }

    /** {@code ShareDto}: who receives which part of an escrow or a cash collection. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Share(String role, UUID walletId, BigDecimal amount) {
        public static final String PLATFORM = "PLATFORM";
        public static final String COURIER = "COURIER";
        public static final String MERCHANT = "MERCHANT";
    }

    /** {@code EscrowHoldRequest}: without code for the challenge, with the payer's code for the hold. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EscrowHoldRequest(String subjectType, UUID subjectId, UUID payerWalletId, String currency,
                                    List<Share> shares, String challengeToken, String code, UUID orderId) {

        public EscrowHoldRequest withCode(String token, String payerCode) {
            return new EscrowHoldRequest(subjectType, subjectId, payerWalletId, currency, shares, token, payerCode,
                    orderId);
        }
    }

    /** {@code EscrowResponse}. {@code status}: HELD, FROZEN, RELEASED, REFUNDED (as returned by the Kernel). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Escrow(UUID id, String subjectType, UUID subjectId, UUID payerWalletId, UUID orderId,
                         BigDecimal amount, BigDecimal refundedAmount, String currency, String status,
                         String statusReason, List<Share> shares, Instant createdAt, Instant updatedAt,
                         Instant settledAt) {
    }

    /** {@code ChallengeResponse}: the code is sent to the payer; {@code codePreview} is a hint. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Challenge(String challengeToken, Long expiresInSeconds, String codePreview) {
    }

    /** {@code CashCollectionRequest}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CashCollectionRequest(String subjectType, UUID subjectId, UUID courierWalletId, String currency,
                                        List<Share> shares) {
    }

    /** {@code CashCollectionResponse}. {@code status}: OPEN, COLLECTED, REMITTED, CANCELLED (as returned). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CashCollection(UUID id, String subjectType, UUID subjectId, UUID courierWalletId,
                                 BigDecimal expectedAmount, BigDecimal collectedAmount, BigDecimal amountToRemit,
                                 String currency, String status, List<Share> shares, Instant createdAt,
                                 Instant collectedAt, Instant remittedAt) {
    }

    /** {@code FundResponse}: the platform compensation fund. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fund(UUID walletId, BigDecimal balance) {
    }

    /** {@code CompensationRequest}. */
    public record CompensationRequest(UUID beneficiaryWalletId, BigDecimal amount, String reference) {
    }

    /** {@code CompensationResponse}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Compensation(UUID id, String reference, UUID fundWalletId, UUID beneficiaryWalletId,
                               BigDecimal amount, Instant createdAt) {
    }
}
