package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for WalletEntity.
 * @author MANFOUO Braun
 */
@Repository
public interface WalletR2dbcRepository extends R2dbcRepository<WalletEntity, UUID> {
    Mono<WalletEntity> findByUserIdAndTenantId(UUID userId, UUID tenantId);
    Mono<Boolean> existsByUserIdAndTenantId(UUID userId, UUID tenantId);
    @Query("SELECT * FROM billing.wallet_wallets WHERE owner_id = :ownerId AND tenant_id = :tenantId LIMIT 1")
    reactor.core.publisher.Mono<com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletEntity> findByOwnerIdAndTenantId(String ownerId, java.util.UUID tenantId);

}