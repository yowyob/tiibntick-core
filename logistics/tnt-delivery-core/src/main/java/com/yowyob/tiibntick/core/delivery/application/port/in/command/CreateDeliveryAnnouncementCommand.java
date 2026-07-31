package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to create a new delivery announcement (published by a client/sender).
 *
 * <p>{@code offeredAmount} is required for {@link AnnouncementPricingMode#FIXED_PRICE};
 * it may be null for {@link AnnouncementPricingMode#QUOTE_REQUEST} (domain validates).
 *
 * @author MANFOUO Braun
 */
public record CreateDeliveryAnnouncementCommand(
        @NotNull UUID tenantId,
        @NotNull UUID clientId,
        @NotBlank String title,
        String description,
        @DecimalMin("1") BigDecimal offeredAmount,
        @NotBlank String currency,
        @NotNull AnnouncementPricingMode pricingMode,
        @NotNull PackageSpecification packageSpec,
        @NotNull DeliveryAddress pickupAddress,
        @NotNull DeliveryAddress deliveryAddress,
        @NotNull RecipientInfo recipient,
        @NotNull DeliveryUrgency urgency
) {
    /**
     * Backward-compatible constructor defaulting to {@link AnnouncementPricingMode#FIXED_PRICE}.
     */
    public CreateDeliveryAnnouncementCommand(
            UUID tenantId,
            UUID clientId,
            String title,
            String description,
            BigDecimal offeredAmount,
            String currency,
            PackageSpecification packageSpec,
            DeliveryAddress pickupAddress,
            DeliveryAddress deliveryAddress,
            RecipientInfo recipient,
            DeliveryUrgency urgency) {
        this(tenantId, clientId, title, description, offeredAmount, currency,
                AnnouncementPricingMode.FIXED_PRICE, packageSpec, pickupAddress,
                deliveryAddress, recipient, urgency);
    }
}
