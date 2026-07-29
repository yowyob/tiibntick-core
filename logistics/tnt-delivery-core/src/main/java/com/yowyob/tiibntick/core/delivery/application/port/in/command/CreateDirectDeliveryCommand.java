package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Command to create a {@code Delivery} directly, bypassing the announcement/response-selection
 * marketplace flow — for callers (e.g. an agency's own client-intake dispatch) that already
 * know who the sender/recipient/parcel are and don't need a bidding step.
 *
 * @author MANFOUO Braun
 */
public record CreateDirectDeliveryCommand(
        UUID tenantId,
        UUID senderId,
        UUID agencyId,
        DeliveryAddress pickupAddress,
        DeliveryAddress deliveryAddress,
        RecipientInfo recipient,
        DeliveryUrgency urgency,
        PackageSpecification packageSpecification,
        Instant scheduledPickupTime,
        String notes
) {}
