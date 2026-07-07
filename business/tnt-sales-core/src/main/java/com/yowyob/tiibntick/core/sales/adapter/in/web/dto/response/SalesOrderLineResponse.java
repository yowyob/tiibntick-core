package com.yowyob.tiibntick.core.sales.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrderLine;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * API response DTO for a single SalesOrderLine.
 * Author: MANFOUO Braun
 */
public record SalesOrderLineResponse(
        UUID productId, String productName, String sku,
        BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineAmount,
        String currency, String notes) {

    public static SalesOrderLineResponse from(TntSalesOrderLine l) {
        return new SalesOrderLineResponse(l.productId(), l.productName(), l.sku(),
                l.quantity(), l.unitPrice(), l.lineAmount(), l.currency(), l.notes());
    }
}
