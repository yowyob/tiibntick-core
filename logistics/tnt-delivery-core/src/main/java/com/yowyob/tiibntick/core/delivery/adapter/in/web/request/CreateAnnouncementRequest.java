package com.yowyob.tiibntick.core.delivery.adapter.in.web.request;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * HTTP request body for creating a new delivery announcement.
 *
 * @author MANFOUO Braun
 */
public record CreateAnnouncementRequest(

        @NotNull UUID clientId,
        @NotBlank String title,
        String description,
        @NotNull @DecimalMin("1") BigDecimal offeredAmount,
        @NotBlank String currency,

        // Package
        @NotNull @Positive double weightKg,
        @NotNull @Positive double widthCm,
        @NotNull @Positive double heightCm,
        @NotNull @Positive double lengthCm,
        boolean fragile,
        boolean perishable,
        String packageDescription,

        // Pickup
        String pickupStreet,
        String pickupLandmark,
        @NotBlank String pickupDistrict,
        @NotBlank String pickupCity,
        @NotNull double pickupLatitude,
        @NotNull double pickupLongitude,

        // Delivery
        String deliveryStreet,
        String deliveryLandmark,
        @NotBlank String deliveryDistrict,
        @NotBlank String deliveryCity,
        @NotNull double deliveryLatitude,
        @NotNull double deliveryLongitude,

        // Recipient
        @NotBlank String recipientName,
        @NotBlank String recipientPhone,
        String recipientAltPhone,

        @NotNull DeliveryUrgency urgency
) {

    public CreateDeliveryAnnouncementCommand toCommand(UUID tenantId) {
        PackageSpecification spec = new PackageSpecification(
                weightKg, widthCm, heightCm, lengthCm, fragile, perishable, packageDescription);

        DeliveryAddress pickup = new DeliveryAddress(pickupStreet, pickupLandmark,
                pickupDistrict, pickupCity, "CM",
                new GeoCoordinates(pickupLatitude, pickupLongitude));

        DeliveryAddress delivery = new DeliveryAddress(deliveryStreet, deliveryLandmark,
                deliveryDistrict, deliveryCity, "CM",
                new GeoCoordinates(deliveryLatitude, deliveryLongitude));

        RecipientInfo recipient = new RecipientInfo(recipientName, recipientPhone, recipientAltPhone);

        return new CreateDeliveryAnnouncementCommand(tenantId, clientId, title, description,
                offeredAmount, currency, spec, pickup, delivery, recipient, urgency);
    }
}
