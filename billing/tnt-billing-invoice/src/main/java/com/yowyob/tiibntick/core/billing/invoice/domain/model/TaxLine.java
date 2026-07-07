package com.yowyob.tiibntick.core.billing.invoice.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object: TaxLine.
 *
 * <p>Represents a tax component on an invoice.
 * Supports multi-country VAT rates:
 * <ul>
 *   <li>Cameroon (CM): 19.25% TVA</li>
 *   <li>Nigeria  (NG): 7.5%  VAT</li>
 *   <li>Kenya    (KE): 16%   VAT</li>
 * </ul>
 * </p>
 *
 * @author MANFOUO Braun
 */
public record TaxLine(
        String taxName,
        String taxCode,
        BigDecimal ratePercent,
        Money taxableBase,
        Money taxAmount,
        String countryCode
) {
    public TaxLine {
        Objects.requireNonNull(taxName, "taxName is required");
        Objects.requireNonNull(taxCode, "taxCode is required");
        Objects.requireNonNull(ratePercent, "ratePercent is required");
        Objects.requireNonNull(taxableBase, "taxableBase is required");
        Objects.requireNonNull(taxAmount, "taxAmount is required");
        Objects.requireNonNull(countryCode, "countryCode is required");
    }

    // ─── Country-specific factory methods ────────────────────────────────────

    /**
     * Cameroon TVA: 19.25%
     */
    public static TaxLine vatCameroon(Money taxableBase) {
        BigDecimal rate = new BigDecimal("19.25");
        return new TaxLine("TVA Cameroun", "CM-TVA-1925", rate,
                taxableBase, taxableBase.percentage(rate), "CM");
    }

    /**
     * Nigeria VAT: 7.5%
     */
    public static TaxLine vatNigeria(Money taxableBase) {
        BigDecimal rate = new BigDecimal("7.5");
        return new TaxLine("Nigeria VAT", "NG-VAT-750", rate,
                taxableBase, taxableBase.percentage(rate), "NG");
    }

    /**
     * Kenya VAT: 16%
     */
    public static TaxLine vatKenya(Money taxableBase) {
        BigDecimal rate = new BigDecimal("16.0");
        return new TaxLine("Kenya VAT", "KE-VAT-1600", rate,
                taxableBase, taxableBase.percentage(rate), "KE");
    }

    /**
     * Creates an appropriate TaxLine for a given country code.
     *
     * @param countryCode ISO 3166-1 alpha-2 code
     * @param taxableBase the taxable base amount
     * @return the TaxLine for that country, or a zero-rate line if unknown
     */
    public static TaxLine forCountry(String countryCode, Money taxableBase) {
        return switch (countryCode.toUpperCase()) {
            case "CM" -> vatCameroon(taxableBase);
            case "NG" -> vatNigeria(taxableBase);
            case "KE" -> vatKenya(taxableBase);
            default   -> new TaxLine("VAT", "UNKNOWN-VAT", BigDecimal.ZERO,
                    taxableBase, Money.zero(taxableBase.currency()), countryCode);
        };
    }
}
