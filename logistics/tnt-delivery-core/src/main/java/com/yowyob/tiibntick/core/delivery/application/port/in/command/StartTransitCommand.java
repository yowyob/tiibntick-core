package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to start transit for a delivery — delivery person has left pickup point.
 *
 * @author MANFOUO Braun
 */
public record StartTransitCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId,
        GeoCoordinates currentPosition
) {}
