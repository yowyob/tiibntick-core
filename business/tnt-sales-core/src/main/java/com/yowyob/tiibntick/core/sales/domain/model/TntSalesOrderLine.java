package com.yowyob.tiibntick.core.sales.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * One line item in a TiiBnTick SalesOrder.
 * Author: MANFOUO Braun
 */
public record TntSalesOrderLine(
        UUID productId,
        String productName,
        String sku,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount,
        String currency,
        String notes
) {

    public TntSalesOrderLine {
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(quantity, "quantity is required");
        Objects.requireNonNull(unitPrice, "unitPrice is required");
        Objects.requireNonNull(currency, "currency is required");
        if (quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (unitPrice.signum() <= 0) throw new IllegalArgumentException("unitPrice must be positive");
    }

    public static TntSalesOrderLine create(UUID productId, String productName, String sku,
                                            BigDecimal quantity, BigDecimal unitPrice, String currency) {
        return new TntSalesOrderLine(productId, productName, sku, quantity, unitPrice,
                quantity.multiply(unitPrice), currency, null);
    }

    public static TntSalesOrderLine withNotes(UUID productId, String productName, String sku,
                                               BigDecimal quantity, BigDecimal unitPrice,
                                               String currency, String notes) {
        return new TntSalesOrderLine(productId, productName, sku, quantity, unitPrice,
                quantity.multiply(unitPrice), currency, notes);
    }
}
