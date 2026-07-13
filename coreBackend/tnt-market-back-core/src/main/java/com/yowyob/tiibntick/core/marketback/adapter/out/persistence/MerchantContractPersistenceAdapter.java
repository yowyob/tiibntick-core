package com.yowyob.tiibntick.core.marketback.adapter.out.persistence;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper.MerchantContractMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository.R2dbcMerchantContractRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMerchantContractRepository;
import com.yowyob.tiibntick.core.marketback.domain.model.ContractId;
import com.yowyob.tiibntick.core.marketback.domain.model.ContractStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.MerchantContract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter — MerchantContract persistence, backed by R2DBC on tnt_market.merchant_contracts.
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class MerchantContractPersistenceAdapter implements IMerchantContractRepository {

    private final R2dbcMerchantContractRepository r2dbcRepository;
    private final MerchantContractMapper mapper;

    @Override
    public Mono<MerchantContract> save(MerchantContract contract) {
        return r2dbcRepository.existsById(contract.getId().value())
                .flatMap(exists -> r2dbcRepository.save(mapper.toEntity(contract, !exists)))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<MerchantContract> findById(ContractId id) {
        return r2dbcRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Flux<MerchantContract> findByMerchantId(UUID merchantId, String tenantId) {
        return r2dbcRepository.findByMerchantIdAndTenantId(merchantId, tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<MerchantContract> findByProviderId(UUID providerId, String tenantId) {
        return r2dbcRepository.findByProviderIdAndTenantId(providerId, tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<MerchantContract> findActiveByMerchantId(UUID merchantId, String tenantId) {
        return r2dbcRepository.findByMerchantIdAndStatusAndTenantId(merchantId, ContractStatus.ACTIVE.name(), tenantId)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> delete(ContractId id) {
        return r2dbcRepository.deleteById(id.value());
    }
}
