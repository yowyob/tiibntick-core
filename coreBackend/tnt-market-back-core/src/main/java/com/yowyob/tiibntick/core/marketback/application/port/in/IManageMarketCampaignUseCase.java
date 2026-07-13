package com.yowyob.tiibntick.core.marketback.application.port.in;

import com.yowyob.tiibntick.core.marketback.application.port.in.command.*;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port — MarketCampaign management use cases.
 * @author MANFOUO Braun
 */
public interface IManageMarketCampaignUseCase {

    Mono<MarketCampaignResponse> createCampaign(CreateCampaignCommand command);
    Mono<MarketCampaignResponse> activateCampaign(UUID campaignId, String tenantId);
    Mono<MarketCampaignResponse> pauseCampaign(UUID campaignId, String tenantId);
    Mono<MarketCampaignResponse> terminateCampaign(UUID campaignId, String tenantId);
    Mono<MarketCampaignResponse> validatePromoCode(String code, UUID orderId, String tenantId);
    Mono<MarketCampaignResponse> getCampaign(UUID campaignId, String tenantId);
    Flux<MarketCampaignResponse> getActiveCampaigns(String tenantId);
    Flux<MarketCampaignResponse> getAllCampaigns(String tenantId);
}
