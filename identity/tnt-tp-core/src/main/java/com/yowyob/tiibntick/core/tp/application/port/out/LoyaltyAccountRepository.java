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
}
