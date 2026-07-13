package com.yowyob.tiibntick.core.linkback.adapter.in.web.request;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Reuses tnt-delivery-core's own value objects directly (PackageSpecification,
 * DeliveryAddress, RecipientInfo) rather than inventing parallel Link-specific
 * shapes for exactly the same data.
 */
public record PublishBoardEntryRequest(
        @NotBlank String title,
        String description,
        @NotNull @DecimalMin("1") BigDecimal offeredAmount,
        @NotBlank String currency,
        @NotNull PackageSpecification packageSpec,
        @NotNull DeliveryAddress pickupAddress,
        @NotNull DeliveryAddress deliveryAddress,
        @NotNull RecipientInfo recipient,
        @NotNull DeliveryUrgency urgency
) {
}
