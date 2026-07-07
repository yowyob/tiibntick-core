package com.yowyob.tiibntick.core.sales.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line in a CreateSalesOrderRequest.
 * Author: MANFOUO Braun
 */
public record CreateOrderLineRequest(
        @NotNull UUID productId,
        String productName,
        String sku,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal unitPrice,
        @NotBlank String currency,
        String notes
) {}
