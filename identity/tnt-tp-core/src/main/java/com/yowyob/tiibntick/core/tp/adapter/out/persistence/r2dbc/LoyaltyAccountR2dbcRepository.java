package com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc;

import com.yowyob.tiibntick.core.tp.adapter.out.persistence.entity.LoyaltyAccountEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for LoyaltyAccountEntity.
 *
 * @author MANFOUO Braun
 */
public interface LoyaltyAccountR2dbcRepository
        extends ReactiveCrudRepository<LoyaltyAccountEntity, UUID> {

    @Query("SELECT * FROM tnt_loyalty_accounts WHERE tenant_id = :tenantId AND third_party_id = :thirdPartyId")
    Mono<LoyaltyAccountEntity> findByTenantIdAndThirdPartyId(UUID tenantId, UUID thirdPartyId);

    @Query("SELECT COUNT(*) > 0 FROM tnt_loyalty_accounts WHERE tenant_id = :tenantId AND third_party_id = :thirdPartyId")
    Mono<Boolean> existsByTenantIdAndThirdPartyId(UUID tenantId, UUID thirdPartyId);
}
