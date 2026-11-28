package com.yowyob.tiibntick.core.tp.application.port.out;

import com.yowyob.tiibntick.core.tp.domain.model.LoyaltyAccount;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port: persistence for LoyaltyAccount.
 *
 * @author MANFOUO Braun
 */
public interface LoyaltyAccountRepository {

    Mono<LoyaltyAccount> save(LoyaltyAccount account);

    Mono<LoyaltyAccount> findByThirdPartyId(UUID tenantId, UUID thirdPartyId);

    Mono<LoyaltyAccount> findById(UUID accountId);

    Mono<Boolean> existsByThirdPartyId(UUID tenantId, UUID thirdPartyId);

    /**
     * Records a points movement of an account once per {@code (account, type, externalRef)}: {@code true} when
     * the row was written, {@code false} when a movement with the same reference already exists (unique index
     * {@code uq_tnt_loyalty_tx_account_type_ref}, lot L3).
     */
    Mono<Boolean> recordTransactionOnce(UUID accountId, int pointsDelta, String type, String externalRef);
}
