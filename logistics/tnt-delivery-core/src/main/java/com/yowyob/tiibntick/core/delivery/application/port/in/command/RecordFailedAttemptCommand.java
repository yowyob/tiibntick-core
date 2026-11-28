package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Rows D9/D10: a hand-over attempt failed for {@code reason} (an attempt reason, QO-3). */
public record RecordFailedAttemptCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId,
        @NotNull DeliveryFailureReason reason
) {}
