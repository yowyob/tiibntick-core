package com.yowyob.tiibntick.core.marketback.application.port.in;

import com.yowyob.tiibntick.core.marketback.application.port.in.command.*;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port — MerchantContract use cases.
 * @author MANFOUO Braun
 */
public interface IManageMerchantContractUseCase {

    Mono<MerchantContractResponse> initNegotiation(InitContractNegotiationCommand command);
    Mono<MerchantContractResponse> signByMerchant(UUID contractId, UUID merchantId, String tenantId);
    Mono<MerchantContractResponse> countersignByProvider(UUID contractId, UUID providerId, String tenantId);
    Mono<MerchantContractResponse> terminateContract(UUID contractId, String reason, String tenantId);
    Mono<MerchantContractResponse> renewContract(UUID contractId, RenewContractCommand command, String tenantId);
    Mono<MerchantContractResponse> getContract(UUID contractId, String tenantId);
    Flux<MerchantContractResponse> getMerchantContracts(UUID merchantId, String tenantId);
    Flux<MerchantContractResponse> getProviderContracts(UUID providerId, String tenantId);
}
