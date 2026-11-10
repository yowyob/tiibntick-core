package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param commissionRate platform commission as a fraction (0.05 = 5 %); 0 for none
 */
public record RecordSettlementCommand(
        UUID tenantId,
        String referenceType,
        UUID referenceId,
        UUID payerUserId,
        UUID payeeUserId,
        BigDecimal amount,
        String currency,
        SettlementMethod method,
        SettlementStatus status,
        String kernelReference,
        BigDecimal commissionRate) {
}
