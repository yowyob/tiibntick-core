package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletTransactionEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for WalletTransactionEntity.
 * @author MANFOUO Braun
 */
@Repository
public interface WalletTransactionR2dbcRepository extends R2dbcRepository<WalletTransactionEntity, UUID> {
    Flux<WalletTransactionEntity> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
    Mono<WalletTransactionEntity> findByIdempotencyKey(String idempotencyKey);
}
