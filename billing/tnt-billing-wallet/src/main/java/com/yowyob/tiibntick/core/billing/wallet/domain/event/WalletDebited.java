package com.yowyob.tiibntick.core.billing.wallet.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;

/**
 * Domain event — emitted when a wallet is debited.
 * @author MANFOUO Braun
 */
public record WalletDebited(
        UUID walletId,
        UUID userId,
        UUID tenantId,
        Money debitedAmount,
        Money balanceAfter,
        String referenceId,
        LocalDateTime occurredAt
) {
    public WalletDebited(UUID walletId, UUID userId, UUID tenantId,
                         Money debitedAmount, Money balanceAfter, String referenceId) {
        this(walletId, userId, tenantId, debitedAmount, balanceAfter, referenceId, LocalDateTime.now());
    }
}
