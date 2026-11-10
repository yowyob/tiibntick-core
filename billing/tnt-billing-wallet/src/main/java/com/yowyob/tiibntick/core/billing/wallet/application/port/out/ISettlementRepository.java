package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ISettlementRepository {
    Mono<WalletSettlement> save(WalletSettlement settlement);
    Mono<WalletSettlement> findById(UUID id);
    Mono<WalletSettlement> findByReference(UUID tenantId, String referenceType, UUID referenceId);
    /** Tenant-agnostic lookup used by incident flows that only carry the business object id. */
    Mono<WalletSettlement> findByReference(String referenceType, UUID referenceId);
    Flux<WalletSettlement> findByTenantAndCommissionStatus(UUID tenantId, CommissionStatus status);
}
