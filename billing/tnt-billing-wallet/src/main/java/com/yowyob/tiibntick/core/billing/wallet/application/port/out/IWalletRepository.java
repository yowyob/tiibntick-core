package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.Wallet;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletId;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletTransaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Secondary port — persistence contract for Wallet aggregate.
 * @author MANFOUO Braun
 */
public interface IWalletRepository {
    Mono<Wallet> save(Wallet wallet);
    Mono<Wallet> findByUserId(UUID userId, UUID tenantId);
    Mono<Wallet> findById(WalletId walletId);
    Mono<WalletTransaction> saveTransaction(WalletTransaction transaction);
    Flux<WalletTransaction> findTransactionsByWalletId(WalletId walletId);
    Mono<Boolean> existsByUserId(UUID userId, UUID tenantId);
    /**
     * Finds a wallet by its owner entity ID (FreelancerOrg or Agency UUID).
     * Used to locate org wallets for revenue crediting.
     *
     * @param ownerId  UUID string of the owning entity (FreelancerOrg or Agency)
     * @param tenantId tenant context
     * @return the wallet, or empty if not yet created
     */
    Mono<Wallet> findByOwnerId(String ownerId, UUID tenantId);

}