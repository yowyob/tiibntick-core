package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletSettlementEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface WalletSettlementR2dbcRepository extends R2dbcRepository<WalletSettlementEntity, UUID> {
    Mono<WalletSettlementEntity> findByTenantIdAndReferenceTypeAndReferenceId(UUID tenantId, String referenceType, UUID referenceId);
    Mono<WalletSettlementEntity> findFirstByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(String referenceType, UUID referenceId);
    Flux<WalletSettlementEntity> findByTenantIdAndCommissionStatusOrderByCreatedAtAsc(UUID tenantId, String commissionStatus);
}
