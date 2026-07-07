package com.yowyob.tiibntick.core.billing.invoice.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.LineItemType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object: InvoiceLine.
 * Represents one billable item on an invoice.
 *
 * @author MANFOUO Braun
 */
public record InvoiceLine(
        int lineNumber,
        String description,
        double quantity,
        Money unitPrice,
        Money lineTotal,
        BigDecimal taxRatePercent,
        Money lineTax,
        LineItemType type
) {
    public InvoiceLine {
        Objects.requireNonNull(description, "description is required");
        Objects.requireNonNull(unitPrice, "unitPrice is required");
        Objects.requireNonNull(type, "type is required");
    }

    /**
     * Builds an InvoiceLine and computes derived totals (full form).
     */
    public static InvoiceLine of(
            int lineNumber, String description, double quantity,
            Money unitPrice, BigDecimal taxRatePercent, LineItemType type) {
        Money lineTotal = unitPrice.multiply(quantity);
        Money lineTax = taxRatePercent != null && taxRatePercent.compareTo(BigDecimal.ZERO) > 0
                ? lineTotal.percentage(taxRatePercent)
                : Money.zero(unitPrice.currency());
        return new InvoiceLine(lineNumber, description, quantity, unitPrice,
                lineTotal, taxRatePercent, lineTax, type);
    }

    /**
     * Simplified factory method: creates an InvoiceLine without line number and tax rate.
     * Useful for quick instantiation in tests or simple scenarios.
     *
     * @param description the item description
     * @param type the line item type
     * @param unitPrice the price per unit
     * @param quantity the quantity
     * @param taxRatePercent the tax rate (nullable)
     * @return a new InvoiceLine with lineNumber=0 and computed totals
     */
    public static InvoiceLine create(String description, LineItemType type,
                                     Money unitPrice, int quantity, BigDecimal taxRatePercent) {
        return InvoiceLine.of(0, description, quantity, unitPrice, taxRatePercent, type);
    }
}
