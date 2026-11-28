package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletCompensation;
import reactor.core.publisher.Mono;

/** Mirrors of the compensations paid by the Kernel fund ({@code billing.wallet_compensations}). */
public interface IWalletCompensationRepository {

    Mono<WalletCompensation> findByDisputeId(String disputeId);

    /** Inserts; the dispute id is unique. */
    Mono<WalletCompensation> insert(WalletCompensation compensation);
}
