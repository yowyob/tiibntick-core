package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.core.dispute.domain.enums.CompensationMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value Object describing a compensation approved in a dispute ruling.
 * Holds the amount, currency, method, beneficiary, and payment status.
 *
 * @author MANFOUO Braun
 */
public final class CompensationDetails {

    private final BigDecimal amount;
    private final String currency;
    private final CompensationMethod method;
    private final String beneficiaryId;
    private final String paymentReference;
    private final LocalDateTime approvedAt;
    private final LocalDateTime paidAt;

    private CompensationDetails(
            final BigDecimal amount,
            final String currency,
            final CompensationMethod method,
            final String beneficiaryId,
            final String paymentReference,
            final LocalDateTime approvedAt,
            final LocalDateTime paidAt) {
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.beneficiaryId = beneficiaryId;
        this.paymentReference = paymentReference;
        this.approvedAt = Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        this.paidAt = paidAt;
    }

    public static CompensationDetails approved(
            final BigDecimal amount,
            final String currency,
            final CompensationMethod method,
            final String beneficiaryId) {
        return new CompensationDetails(amount, currency, method, beneficiaryId, null, LocalDateTime.now(), null);
    }

    /**
     * Returns a new instance marked as paid with the given payment reference.
     *
     * @param paymentReference the transaction reference from the payment gateway
     * @return a paid {@code CompensationDetails}
     */
    public CompensationDetails markAsPaid(final String paymentReference) {
        return new CompensationDetails(
                amount, currency, method, beneficiaryId, paymentReference, approvedAt, LocalDateTime.now());
    }

    public boolean isPaid() {
        return paidAt != null;
    }

    public String formattedAmount() {
        return "%s %,.2f".formatted(currency, amount);
    }

    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public CompensationMethod getMethod() { return method; }
    public String getBeneficiaryId() { return beneficiaryId; }
    public String getPaymentReference() { return paymentReference; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public LocalDateTime getPaidAt() { return paidAt; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof CompensationDetails that)) return false;
        return amount.compareTo(that.amount) == 0
                && currency.equals(that.currency)
                && method == that.method
                && Objects.equals(beneficiaryId, that.beneficiaryId)
                && approvedAt.equals(that.approvedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency, method, beneficiaryId, approvedAt);
    }
}
