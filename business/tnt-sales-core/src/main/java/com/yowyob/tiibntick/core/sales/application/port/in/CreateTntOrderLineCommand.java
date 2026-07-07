package com.yowyob.tiibntick.core.sales.application.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One order line within CreateTntSalesOrderCommand.
 * Author: MANFOUO Braun
 */
public record CreateTntOrderLineCommand(
        @NotNull UUID productId,
        String productName,
        String sku,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal unitPrice,
        @NotBlank String currency,
        String notes
) {}
