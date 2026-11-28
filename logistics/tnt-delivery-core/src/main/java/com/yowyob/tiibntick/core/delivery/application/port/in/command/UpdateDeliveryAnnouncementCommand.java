package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.AnnouncementDetails;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.SenderInfo;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to amend an announcement of the calling client. Every optional component left null means
 * "unchanged"; non-null components of {@code details} replace the stored ones.
 *
 * @author MANFOUO Braun
 */
public record UpdateDeliveryAnnouncementCommand(
        @NotNull UUID tenantId,
        @NotNull UUID announcementId,
        @NotNull UUID clientId,
        BigDecimal offeredAmount,
        String currency,
        SenderInfo sender,
        AnnouncementDetails details
) {
}
