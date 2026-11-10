package com.yowyob.tiibntick.core.billing.wallet.application.port.in;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Tracks which business objects were settled via the Kernel wallet (or cash) and the commission due. */
public interface ISettlementUseCase {

    /**
     * Idempotent upsert keyed on (tenant, referenceType, referenceId). A settlement already
     * PAID is never downgraded to UNPAID/CASH_COLLECTED.
     */
    Mono<WalletSettlement> record(RecordSettlementCommand command);

    Mono<WalletSettlement> find(UUID tenantId, String referenceType, UUID referenceId);

    /** Platform commissions still DUE for the tenant, oldest first. */
    Flux<WalletSettlement> listCommissionsDue(UUID tenantId);

    /** Marks the commission SETTLED (collected out-of-band). */
    Mono<WalletSettlement> markCommissionSettled(UUID tenantId, UUID settlementId);
}
