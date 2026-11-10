package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletHold;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IWalletHoldRepository {
    Mono<WalletHold> save(WalletHold hold);
    Flux<WalletHold> findActiveByOwner(UUID ownerUserId);
    Flux<WalletHold> findActiveByReference(String referenceType, UUID referenceId);
}
