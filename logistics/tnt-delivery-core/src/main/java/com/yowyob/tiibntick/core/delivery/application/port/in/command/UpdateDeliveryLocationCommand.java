package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to update the real-time location of a delivery person during transit.
 * Triggers Kalman filter ETA re-estimation.
 *
 * @author MANFOUO Braun
 */
public record UpdateDeliveryLocationCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId,
        @NotNull GeoCoordinates currentPosition
) {}
