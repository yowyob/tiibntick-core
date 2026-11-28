package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.AnnouncementDetails;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.SenderInfo;
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
 * <p>{@code existingParcel} is optional: when the goods were registered beforehand through
 * {@code ParcelUseCase}, the announcement (and the delivery created from it) reuse that parcel and its
 * id instead of creating a new one from {@code packageSpec}.
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
        @NotNull DeliveryUrgency urgency,
        Parcel existingParcel,
        /** Optional sender contact (null = the announcing client's own account). */
        SenderInfo sender,
        /** Optional generic complements (null = none). */
        AnnouncementDetails details
) {
    /**
     * Backward-compatible constructor: the parcel is created from {@code packageSpec} when the
     * announcement is published.
     */
    public CreateDeliveryAnnouncementCommand(
            UUID tenantId,
            UUID clientId,
            String title,
            String description,
            BigDecimal offeredAmount,
            String currency,
            AnnouncementPricingMode pricingMode,
            PackageSpecification packageSpec,
            DeliveryAddress pickupAddress,
            DeliveryAddress deliveryAddress,
            RecipientInfo recipient,
            DeliveryUrgency urgency) {
        this(tenantId, clientId, title, description, offeredAmount, currency, pricingMode, packageSpec,
                pickupAddress, deliveryAddress, recipient, urgency, null, null, null);
    }

    /** Goods registered beforehand ({@code existingParcel}), no sender, no details. */
    public CreateDeliveryAnnouncementCommand(
            UUID tenantId,
            UUID clientId,
            String title,
            String description,
            BigDecimal offeredAmount,
            String currency,
            AnnouncementPricingMode pricingMode,
            PackageSpecification packageSpec,
            DeliveryAddress pickupAddress,
            DeliveryAddress deliveryAddress,
            RecipientInfo recipient,
            DeliveryUrgency urgency,
            Parcel existingParcel) {
        this(tenantId, clientId, title, description, offeredAmount, currency, pricingMode, packageSpec,
                pickupAddress, deliveryAddress, recipient, urgency, existingParcel, null, null);
    }

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
                deliveryAddress, recipient, urgency, null, null, null);
    }
}
