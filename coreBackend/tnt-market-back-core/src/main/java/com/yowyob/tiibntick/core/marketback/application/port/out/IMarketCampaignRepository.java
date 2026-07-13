package com.yowyob.tiibntick.core.marketback.application.port.out;

import com.yowyob.tiibntick.core.marketback.domain.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port — MarketCampaign persistence contract.
 * @author MANFOUO Braun
 */
public interface IMarketCampaignRepository {
    Mono<MarketCampaign> save(MarketCampaign campaign);
    Mono<MarketCampaign> findById(CampaignId id);
    Flux<MarketCampaign> findActiveCampaigns(String tenantId);
    Mono<MarketCampaign> findByPromoCode(String promoCode, String tenantId);
    Flux<MarketCampaign> findByTenantId(String tenantId);
    Mono<Void> delete(CampaignId id);
}
