package com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Gofp-owned command to amend an announcement through
 * {@link com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort}.
 * Null components are left unchanged.
 *
 * @author MANFOUO BRAUN
 */
public record UpdateAnnouncementPortCommand(
        UUID tenantId,
        UUID announcementId,
        UUID clientId,
        BigDecimal offeredAmount,
        String currency,
        AnnouncementExtras extras) {

    public UpdateAnnouncementPortCommand {
        extras = extras != null ? extras : AnnouncementExtras.EMPTY;
    }
}
