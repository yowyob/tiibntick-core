package com.yowyob.tiibntick.core.dispute.domain.event;

import com.yowyob.tiibntick.core.dispute.domain.enums.CompensationMethod;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain event emitted when compensation has been successfully processed.
 * Consumed by tnt-billing-wallet for final reconciliation and tnt-notify-core.
 *
 * @author MANFOUO Braun
 */
public record CompensationProcessed(
        DisputeId disputeId,
        String tenantId,
        BigDecimal amount,
        String currency,
        CompensationMethod method,
        String beneficiaryId,
        LocalDateTime occurredAt
) {
    public CompensationProcessed {
        Objects.requireNonNull(disputeId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(method);
        Objects.requireNonNull(occurredAt);
    }
}
