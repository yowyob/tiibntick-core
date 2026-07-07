package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.mapper.WalletPersistenceMapper;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.WalletR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.WalletTransactionR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Adapter — implements IWalletRepository using Spring Data R2DBC.
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class WalletRepositoryAdapter implements IWalletRepository {

    private final WalletR2dbcRepository walletRepo;
    private final WalletTransactionR2dbcRepository txRepo;
    private final WalletPersistenceMapper mapper;

    @Override
    public Mono<Wallet> save(Wallet wallet) {
        var entity = mapper.toEntity(wallet);
        return walletRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return walletRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Wallet> findByUserId(UUID userId, UUID tenantId) {
        return walletRepo.findByUserIdAndTenantId(userId, tenantId)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Wallet> findById(WalletId walletId) {
        return walletRepo.findById(walletId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Mono<WalletTransaction> saveTransaction(WalletTransaction transaction) {
        return txRepo.existsById(transaction.getId().value())
                .flatMap(exists -> {
                    var entity = mapper.toEntity(transaction);
                    entity.setNew(!exists);
                    return txRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Flux<WalletTransaction> findTransactionsByWalletId(WalletId walletId) {
        return txRepo.findByWalletIdOrderByCreatedAtDesc(walletId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsByUserId(UUID userId, UUID tenantId) {
        return walletRepo.existsByUserIdAndTenantId(userId, tenantId);
    }
    @Override
    public reactor.core.publisher.Mono<com.yowyob.tiibntick.core.billing.wallet.domain.model.Wallet> findByOwnerId(String ownerId, java.util.UUID tenantId) {
        return walletRepo.findByOwnerIdAndTenantId(ownerId, tenantId)
                .map(mapper::toDomain);
    }

}