package com.yowyob.tiibntick.core.accounting.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when an accounting period is definitively closed.
 * Author: MANFOUO Braun
 */
public record AccountingPeriodClosedEvent(
        UUID periodId,
        UUID tenantId,
        int year,
        int month,
        String periodCode,
        Instant closedAt
) {

    public static AccountingPeriodClosedEvent of(
            com.yowyob.tiibntick.core.accounting.domain.model.AccountingPeriod period) {
        return new AccountingPeriodClosedEvent(
                period.getId(), period.getTenantId(), period.getYear(),
                period.getMonth(), period.periodCode(), Instant.now());
    }
}
