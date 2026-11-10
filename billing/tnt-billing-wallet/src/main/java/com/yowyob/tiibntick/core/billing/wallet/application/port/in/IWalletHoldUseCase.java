package com.yowyob.tiibntick.core.billing.wallet.application.port.in;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletHold;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Local replacement for the freeze/reservation the Kernel does not offer. A hold does not move
 * money: it makes the Core refuse outgoing operations that would dip into the held amount.
 */
public interface IWalletHoldUseCase {

    /** Idempotent per (owner, referenceType, referenceId): returns the existing ACTIVE hold if any. */
    Mono<WalletHold> placeHold(UUID tenantId, UUID ownerUserId, String referenceType, UUID referenceId,
                               BigDecimal amount, String currency, String reason);

    /**
     * Holds the amount received by the beneficiary of the settlement of {@code referenceId}.
     * Completes empty when there is nothing to hold (unpaid, cash, unknown reference).
     */
    Mono<WalletHold> holdSettlement(String referenceType, UUID referenceId, String reason);

    /** Releases every ACTIVE hold of the business object; emits the number released. */
    Mono<Long> releaseHolds(String referenceType, UUID referenceId);

    Flux<WalletHold> listActive(UUID ownerUserId);

    /** Sum of ACTIVE holds of the owner (single-currency: XAF in practice), zero when none. */
    Mono<BigDecimal> activeHeldAmount(UUID ownerUserId, String currency);
}
