package com.yowyob.tiibntick.core.billing.wallet.domain.event;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event — emitted when a commission is calculated for a deliverer
 * after a successful payment confirmation.
 * @author MANFOUO Braun
 */
public record CommissionCalculated(
        UUID delivererId,
        UUID tenantId,
        String missionId,
        String invoiceId,
        Money commissionAmount,
        Money sellingPrice,
        LocalDateTime occurredAt
) {
    public CommissionCalculated(UUID delivererId, UUID tenantId, String missionId,
                                String invoiceId, Money commissionAmount, Money sellingPrice) {
        this(delivererId, tenantId, missionId, invoiceId, commissionAmount, sellingPrice, LocalDateTime.now());
    }
}
