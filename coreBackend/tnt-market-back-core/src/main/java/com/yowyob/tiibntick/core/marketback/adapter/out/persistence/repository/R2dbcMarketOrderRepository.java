package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.MarketOrderEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link MarketOrderEntity}.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcMarketOrderRepository extends ReactiveCrudRepository<MarketOrderEntity, UUID> {

    Mono<MarketOrderEntity> findByIdAndTenantId(UUID id, String tenantId);

    Flux<MarketOrderEntity> findByClientIdAndTenantId(UUID clientId, String tenantId);

    Flux<MarketOrderEntity> findByProviderIdAndTenantId(UUID providerId, String tenantId);

    Flux<MarketOrderEntity> findByStatusAndTenantId(String status, String tenantId);

    @Query("SELECT * FROM tnt_market.market_orders WHERE listing_id = :listingId")
    Flux<MarketOrderEntity> findByListingId(UUID listingId);

    Mono<Long> countByTenantId(String tenantId);

    @Query("SELECT COUNT(*) FROM tnt_market.market_orders WHERE tenant_id = :tenantId AND status = 'COMPLETED'")
    Mono<Long> countCompletedByTenantId(String tenantId);

    Mono<Long> countByProviderIdAndTenantId(UUID providerId, String tenantId);

    @Query("SELECT COALESCE(SUM(total_amount), 0) FROM tnt_market.market_orders "
            + "WHERE provider_id = :providerId AND tenant_id = :tenantId AND status = 'COMPLETED'")
    Mono<BigDecimal> sumRevenueByProviderId(UUID providerId, String tenantId);
}
