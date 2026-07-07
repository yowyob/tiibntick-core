package com.yowyob.tiibntick.core.delivery.application.port.in.command;

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
 * @author MANFOUO Braun
 */
public record CreateDeliveryAnnouncementCommand(
        @NotNull UUID tenantId,
        @NotNull UUID clientId,
        @NotBlank String title,
        String description,
        @NotNull @DecimalMin("1") BigDecimal offeredAmount,
        @NotBlank String currency,
        @NotNull PackageSpecification packageSpec,
        @NotNull DeliveryAddress pickupAddress,
        @NotNull DeliveryAddress deliveryAddress,
        @NotNull RecipientInfo recipient,
        @NotNull DeliveryUrgency urgency
) {}
