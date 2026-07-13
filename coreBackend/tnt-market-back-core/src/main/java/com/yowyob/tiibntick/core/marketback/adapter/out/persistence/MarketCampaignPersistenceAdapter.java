package com.yowyob.tiibntick.core.marketback.adapter.out.persistence;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper.MarketCampaignMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository.R2dbcMarketCampaignRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketCampaignRepository;
import com.yowyob.tiibntick.core.marketback.domain.model.CampaignId;
import com.yowyob.tiibntick.core.marketback.domain.model.CampaignStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketCampaign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound adapter — MarketCampaign persistence, backed by R2DBC on tnt_market.market_campaigns.
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class MarketCampaignPersistenceAdapter implements IMarketCampaignRepository {

    private final R2dbcMarketCampaignRepository r2dbcRepository;
    private final MarketCampaignMapper mapper;

    @Override
    public Mono<MarketCampaign> save(MarketCampaign campaign) {
        return r2dbcRepository.existsById(campaign.getId().value())
                .flatMap(exists -> r2dbcRepository.save(mapper.toEntity(campaign, !exists)))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<MarketCampaign> findById(CampaignId id) {
        return r2dbcRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Flux<MarketCampaign> findActiveCampaigns(String tenantId) {
        return r2dbcRepository.findByStatusAndTenantId(CampaignStatus.ACTIVE.name(), tenantId).map(mapper::toDomain);
    }

    @Override
    public Mono<MarketCampaign> findByPromoCode(String promoCode, String tenantId) {
        return r2dbcRepository.findByPromoCodeAndTenantId(promoCode, tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<MarketCampaign> findByTenantId(String tenantId) {
        return r2dbcRepository.findByTenantId(tenantId).map(mapper::toDomain);
    }

    @Override
    public Mono<Void> delete(CampaignId id) {
        return r2dbcRepository.deleteById(id.value());
    }
}
