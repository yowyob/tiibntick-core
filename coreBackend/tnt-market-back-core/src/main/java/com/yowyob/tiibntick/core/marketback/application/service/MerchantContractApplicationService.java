package com.yowyob.tiibntick.core.marketback.application.service;

import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMerchantContractUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.InitContractNegotiationCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.RenewContractCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MerchantContractResponse;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketEventPublisher;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMerchantContractRepository;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;
import com.yowyob.tiibntick.core.marketback.domain.model.ContractId;
import com.yowyob.tiibntick.core.marketback.domain.model.ContractTerms;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.MerchantContract;
import com.yowyob.tiibntick.core.marketback.domain.model.VolumeTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Application service — merchant volume contract negotiation, signature and renewal.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantContractApplicationService implements IManageMerchantContractUseCase {

    private final IMerchantContractRepository contractRepository;
    private final IMarketEventPublisher eventPublisher;

    // NOTE(market-migration): tnt-tp-core models third-party CLIENTS (buyer-side
    // KYC/loyalty/rating — TntClientProfile, KycRecord, LoyaltyAccount), not
    // merchants — no natural integration point for MerchantContract, which is a
    // platform<->provider agreement (tnt-organization-core/tnt-actor-core territory).
    //
    // No clean tnt-organization-core/tnt-actor-core validation hook either:
    // MerchantContract.providerId is a raw UUID that may refer to an Agency
    // organizationId, a FreelancerOrganization, or a plain actorId (mirrors
    // MarketListing's own "UUID of the provider (actor or organization)" comment),
    // and unlike MarketListing this aggregate carries no ProviderType field to
    // disambiguate which one it is. Guessing which repository to validate against
    // would be as likely to reject a legitimate contract as to catch a bad one, so
    // this is intentionally left unwired rather than forcing a guess.

    @Override
    public Mono<MerchantContractResponse> initNegotiation(InitContractNegotiationCommand command) {
        log.debug("Initiating contract negotiation between merchant={} provider={}", command.merchantId(), command.providerId());
        ContractTerms terms = new ContractTerms(command.baseDiscountPct(), command.maxMonthlyOrders(),
                command.minMonthlyOrders(), command.paymentTermDays(), null, command.specialConditions());
        List<VolumeTier> tiers = command.volumeTiers() == null ? List.of()
                : command.volumeTiers().stream()
                        .map(t -> new VolumeTier(UUID.randomUUID().toString(), t.minOrders(), t.maxOrders(),
                                t.discountPct(), t.flatRateXaf()))
                        .toList();
        MerchantContract contract = MerchantContract.negotiate(command.tenantId(), command.merchantId(),
                command.providerId(), MarketListingId.of(command.listingId()), terms, tiers,
                command.startDate(), command.endDate());

        return contractRepository.save(contract).map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<MerchantContractResponse> signByMerchant(UUID contractId, UUID merchantId, String tenantId) {
        return contractRepository.findById(ContractId.of(contractId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Contract not found: " + contractId)))
                .flatMap(contract -> {
                    contract.signByMerchant(merchantId);
                    return contractRepository.save(contract);
                })
                .flatMap(saved -> eventPublisher.publishAll(saved.pullDomainEvents(), tenantId).thenReturn(saved))
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<MerchantContractResponse> countersignByProvider(UUID contractId, UUID providerId, String tenantId) {
        return contractRepository.findById(ContractId.of(contractId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Contract not found: " + contractId)))
                .flatMap(contract -> {
                    contract.countersignByProvider(providerId);
                    return contractRepository.save(contract);
                })
                .flatMap(saved -> eventPublisher.publishAll(saved.pullDomainEvents(), tenantId).thenReturn(saved))
                .map(this::toResponse);
    }

    @Override
    public Mono<MerchantContractResponse> terminateContract(UUID contractId, String reason, String tenantId) {
        return contractRepository.findById(ContractId.of(contractId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Contract not found: " + contractId)))
                .flatMap(contract -> {
                    contract.terminate(reason);
                    return contractRepository.save(contract);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<MerchantContractResponse> renewContract(UUID contractId, RenewContractCommand command, String tenantId) {
        return contractRepository.findById(ContractId.of(contractId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Contract not found: " + contractId)))
                .flatMap(contract -> {
                    contract.renew(command.newEndDate());
                    return contractRepository.save(contract);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<MerchantContractResponse> getContract(UUID contractId, String tenantId) {
        return contractRepository.findById(ContractId.of(contractId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Contract not found: " + contractId)))
                .map(this::toResponse);
    }

    @Override
    public Flux<MerchantContractResponse> getMerchantContracts(UUID merchantId, String tenantId) {
        return contractRepository.findByMerchantId(merchantId, tenantId).map(this::toResponse);
    }

    @Override
    public Flux<MerchantContractResponse> getProviderContracts(UUID providerId, String tenantId) {
        return contractRepository.findByProviderId(providerId, tenantId).map(this::toResponse);
    }

    private MerchantContractResponse toResponse(MerchantContract contract) {
        ContractTerms terms = contract.getTerms();
        return new MerchantContractResponse(
                contract.getId().value(), contract.getTenantId(), contract.getMerchantId(), contract.getProviderId(),
                contract.getListingId().value(), contract.getStatus(),
                terms != null ? terms.baseDiscountPct() : 0,
                terms != null ? terms.minMonthlyOrders() : 0,
                terms != null ? terms.maxMonthlyOrders() : 0,
                contract.getStartDate(), contract.getEndDate(), contract.getTotalOrdersExecuted(), contract.getTotalAmountXaf(),
                contract.getSignedAt(), contract.getCreatedAt());
    }
}
