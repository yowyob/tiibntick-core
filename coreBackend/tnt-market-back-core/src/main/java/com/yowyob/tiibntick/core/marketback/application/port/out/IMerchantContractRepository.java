package com.yowyob.tiibntick.core.marketback.application.port.out;

import com.yowyob.tiibntick.core.marketback.domain.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Outbound port — MerchantContract persistence contract.
 * @author MANFOUO Braun
 */
public interface IMerchantContractRepository {
    Mono<MerchantContract> save(MerchantContract contract);
    Mono<MerchantContract> findById(ContractId id);
    Flux<MerchantContract> findByMerchantId(UUID merchantId, String tenantId);
    Flux<MerchantContract> findByProviderId(UUID providerId, String tenantId);
    Flux<MerchantContract> findActiveByMerchantId(UUID merchantId, String tenantId);
    Mono<Void> delete(ContractId id);
}
