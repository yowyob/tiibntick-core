package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records that a business object (delivery, relay deposit, market order) was settled — by a
 * Kernel wallet transfer or in cash — and how much platform commission is still due. It is NOT
 * a ledger: balances and movements live in the Kernel wallet.
 */
@Value
@Builder(toBuilder = true)
@With
public class WalletSettlement {
    UUID id;
    UUID tenantId;
    String referenceType;
    UUID referenceId;
    UUID payerUserId;
    UUID payeeUserId;
    BigDecimal amount;
    String currency;
    SettlementMethod method;
    SettlementStatus status;
    String kernelReference;
    BigDecimal commissionRate;
    BigDecimal commissionAmount;
    CommissionStatus commissionStatus;
    LocalDateTime createdAt;
    LocalDateTime paidAt;
    LocalDateTime updatedAt;
    boolean persisted;
}
