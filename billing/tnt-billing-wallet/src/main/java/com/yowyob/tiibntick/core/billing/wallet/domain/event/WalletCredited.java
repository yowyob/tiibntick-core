package com.yowyob.tiibntick.core.billing.wallet.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;

/**
 * Domain event — emitted when a wallet receives a credit.
 * @author MANFOUO Braun
 */
public record WalletCredited(
        UUID walletId,
        UUID userId,
        UUID tenantId,
        Money creditedAmount,
        Money balanceAfter,
        String referenceId,
        LocalDateTime occurredAt
) {
    public WalletCredited(UUID walletId, UUID userId, UUID tenantId,
                          Money creditedAmount, Money balanceAfter, String referenceId) {
        this(walletId, userId, tenantId, creditedAmount, balanceAfter, referenceId, LocalDateTime.now());
    }
}
