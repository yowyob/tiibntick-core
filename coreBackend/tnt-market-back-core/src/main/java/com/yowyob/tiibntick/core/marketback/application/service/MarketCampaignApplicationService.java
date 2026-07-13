package com.yowyob.tiibntick.core.marketback.application.service;

import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketCampaignUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateCampaignCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketCampaignResponse;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketCampaignRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketEventPublisher;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;
import com.yowyob.tiibntick.core.marketback.domain.model.CampaignId;
import com.yowyob.tiibntick.core.marketback.domain.model.CampaignScope;
import com.yowyob.tiibntick.core.marketback.domain.model.DiscountRule;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketCampaign;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Application service — promotional campaign lifecycle (create, activate,
 * pause, terminate) and promo code validation.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketCampaignApplicationService implements IManageMarketCampaignUseCase {

    private final IMarketCampaignRepository campaignRepository;
    private final IMarketEventPublisher eventPublisher;

    // NOTE(market-migration): no genuine external integration point found for
    // MarketCampaign. It is pure marketing/discount configuration (name, type,
    // DiscountRule, CampaignScope, dates, promo code, usage counters) scoped
    // entirely to listings/service-types/provider-types already known within
    // this module — nothing here references an actor, organization, media
    // asset, or third-party client. tnt-notify-core was considered (e.g.
    // broadcasting "new campaign" notifications) but there is no subscriber/
    // audience list on this aggregate to notify, so wiring it would be a
    // contrived, one-way fire-and-forget with no real recipient targeting.
    // Left unwired rather than forcing a call that doesn't do anything useful.

    @Override
    public Mono<MarketCampaignResponse> createCampaign(CreateCampaignCommand command) {
        log.debug("Creating campaign name={} tenant={}", command.name(), command.tenantId());
        DiscountRule discountRule = new DiscountRule(command.discountType(), command.discountValue(),
                command.maxDiscountXaf(), command.minimumOrderXaf());
        List<MarketListingId> targetListingIds = command.targetListingIds() == null ? null
                : command.targetListingIds().stream().map(MarketListingId::of).toList();
        CampaignScope scope = new CampaignScope(command.applyToAll(), targetListingIds,
                command.targetServiceTypes(), command.targetProviderTypes());
        MarketCampaign campaign = MarketCampaign.create(command.tenantId(), command.adminId(), command.name(),
                command.type(), discountRule, scope, command.startDate(), command.endDate(),
                command.maxUsage(), command.promoCode());
        return campaignRepository.save(campaign).map(this::toResponse);
    }

    @Override
    public Mono<MarketCampaignResponse> activateCampaign(UUID campaignId, String tenantId) {
        return campaignRepository.findById(CampaignId.of(campaignId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Campaign not found: " + campaignId)))
                .flatMap(campaign -> {
                    campaign.activate();
                    return campaignRepository.save(campaign);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketCampaignResponse> pauseCampaign(UUID campaignId, String tenantId) {
        return campaignRepository.findById(CampaignId.of(campaignId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Campaign not found: " + campaignId)))
                .flatMap(campaign -> {
                    campaign.pause();
                    return campaignRepository.save(campaign);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketCampaignResponse> terminateCampaign(UUID campaignId, String tenantId) {
        return campaignRepository.findById(CampaignId.of(campaignId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Campaign not found: " + campaignId)))
                .flatMap(campaign -> {
                    campaign.terminate();
                    return campaignRepository.save(campaign);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketCampaignResponse> validatePromoCode(String code, UUID orderId, String tenantId) {
        return campaignRepository.findByPromoCode(code, tenantId)
                .switchIfEmpty(Mono.error(new MarketDomainException("Invalid promo code: " + code)))
                .filter(MarketCampaign::canBeUsed)
                .switchIfEmpty(Mono.error(new MarketDomainException("Promo code not usable: " + code)))
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketCampaignResponse> getCampaign(UUID campaignId, String tenantId) {
        return campaignRepository.findById(CampaignId.of(campaignId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Campaign not found: " + campaignId)))
                .map(this::toResponse);
    }

    @Override
    public Flux<MarketCampaignResponse> getActiveCampaigns(String tenantId) {
        return campaignRepository.findActiveCampaigns(tenantId).map(this::toResponse);
    }

    @Override
    public Flux<MarketCampaignResponse> getAllCampaigns(String tenantId) {
        return campaignRepository.findByTenantId(tenantId).map(this::toResponse);
    }

    private MarketCampaignResponse toResponse(MarketCampaign campaign) {
        DiscountRule discount = campaign.getDiscount();
        return new MarketCampaignResponse(
                campaign.getId().value(), campaign.getTenantId(), campaign.getName(), campaign.getType(), campaign.getStatus(),
                discount != null ? discount.discountType() : null, discount != null ? discount.value() : 0.0,
                campaign.getPromoCode(), campaign.getUsageCount(), campaign.getMaxUsage(),
                campaign.getStartDate(), campaign.getEndDate(), campaign.getCreatedAt());
    }
}
