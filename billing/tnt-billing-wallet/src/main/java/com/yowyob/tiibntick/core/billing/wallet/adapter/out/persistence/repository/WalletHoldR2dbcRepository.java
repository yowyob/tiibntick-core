package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletHoldEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface WalletHoldR2dbcRepository extends R2dbcRepository<WalletHoldEntity, UUID> {
    Flux<WalletHoldEntity> findByOwnerUserIdAndStatus(UUID ownerUserId, String status);
    Flux<WalletHoldEntity> findByReferenceTypeAndReferenceIdAndStatus(String referenceType, UUID referenceId, String status);
}
