package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.MerchantContractEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC repository for MerchantContract.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcMerchantContractRepository extends ReactiveCrudRepository<MerchantContractEntity, UUID> {

    Mono<MerchantContractEntity> findByIdAndTenantId(UUID id, String tenantId);

    Flux<MerchantContractEntity> findByMerchantIdAndTenantId(UUID merchantId, String tenantId);

    Flux<MerchantContractEntity> findByProviderIdAndTenantId(UUID providerId, String tenantId);

    Flux<MerchantContractEntity> findByMerchantIdAndStatusAndTenantId(UUID merchantId, String status, String tenantId);
}
