package com.yowyob.kernel.i18n.domain.vo;

import com.yowyob.kernel.i18n.domain.enums.SupportedCurrency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object representing a price localized to a specific currency and locale.
 * Immutable and self-validating.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public record LocalizedPrice(BigDecimal amount, SupportedCurrency currency, String representation) {

    /**
     * Compact constructor ensuring invariants.
     */
    public LocalizedPrice {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        Objects.requireNonNull(representation, "Representation must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price amount cannot be negative");
        }
    }

    /**
     * Creates a LocalizedPrice from raw numeric value and currency.
     * The representation will be formatted on creation.
     *
     * @param amount the numeric amount
     * @param currency  the target currency
     * @return a new LocalizedPrice instance
     */
    public static LocalizedPrice of(BigDecimal amount, SupportedCurrency currency) {
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        String repr = currency.getSymbol() + " " + rounded.toPlainString();
        return new LocalizedPrice(rounded, currency, repr);
    }

    /**
     * Creates a LocalizedPrice from a long value (e.g., for XAF which has no decimal).
     */
    public static LocalizedPrice ofXaf(long integerAmount) {
        BigDecimal amount = BigDecimal.valueOf(integerAmount).setScale(0, RoundingMode.UNNECESSARY);
        String repr = SupportedCurrency.XAF.getSymbol() + " " + integerAmount;
        return new LocalizedPrice(amount, SupportedCurrency.XAF, repr);
    }
}
