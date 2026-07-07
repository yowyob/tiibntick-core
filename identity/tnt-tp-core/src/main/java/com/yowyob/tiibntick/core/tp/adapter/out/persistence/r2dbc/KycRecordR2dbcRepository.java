package com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc;

import com.yowyob.tiibntick.core.tp.adapter.out.persistence.entity.KycRecordEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for KycRecordEntity.
 *
 * @author MANFOUO Braun
 */
public interface KycRecordR2dbcRepository
        extends ReactiveCrudRepository<KycRecordEntity, UUID> {

    @Query("SELECT * FROM tnt_kyc_records WHERE tenant_id = :tenantId AND third_party_id = :thirdPartyId ORDER BY created_at DESC LIMIT 1")
    Mono<KycRecordEntity> findLatestByTenantIdAndThirdPartyId(UUID tenantId, UUID thirdPartyId);

    @Query("SELECT * FROM tnt_kyc_records WHERE tenant_id = :tenantId AND third_party_id = :thirdPartyId ORDER BY created_at DESC")
    Flux<KycRecordEntity> findAllByTenantIdAndThirdPartyId(UUID tenantId, UUID thirdPartyId);

    @Query("SELECT COUNT(*) > 0 FROM tnt_kyc_records WHERE tenant_id = :tenantId AND third_party_id = :thirdPartyId AND status = 'PENDING_REVIEW'")
    Mono<Boolean> existsPendingByTenantIdAndThirdPartyId(UUID tenantId, UUID thirdPartyId);
}
