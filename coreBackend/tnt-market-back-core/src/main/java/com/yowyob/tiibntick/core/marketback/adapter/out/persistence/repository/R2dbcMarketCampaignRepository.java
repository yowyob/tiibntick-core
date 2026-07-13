package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.MarketCampaignEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC repository for MarketCampaign.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcMarketCampaignRepository extends ReactiveCrudRepository<MarketCampaignEntity, UUID> {

    Mono<MarketCampaignEntity> findByIdAndTenantId(UUID id, String tenantId);

    Flux<MarketCampaignEntity> findByTenantId(String tenantId);

    Flux<MarketCampaignEntity> findByStatusAndTenantId(String status, String tenantId);

    Mono<MarketCampaignEntity> findByPromoCodeAndTenantId(String promoCode, String tenantId);
}
