package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.MarketListingEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link MarketListingEntity}.
 *
 * <p>Ported from {@code tiibntick-market-backend}'s
 * {@code adapter.outbound.persistence.repository.R2dbcMarketListingRepository}.
 * Must live under {@code adapter.out.persistence.repository} to be picked up
 * by {@code MarketBackR2dbcConfig}'s {@code @EnableR2dbcRepositories}.</p>
 *
 * @author MANFOUO Braun
 */
public interface R2dbcMarketListingRepository extends ReactiveCrudRepository<MarketListingEntity, UUID> {

    Flux<MarketListingEntity> findByTenantId(String tenantId);

    Mono<MarketListingEntity> findByIdAndTenantId(UUID id, String tenantId);

    Mono<MarketListingEntity> findByProviderIdAndTenantId(UUID providerId, String tenantId);

    Flux<MarketListingEntity> findByStatusAndTenantId(String status, String tenantId);

    Flux<MarketListingEntity> findByProviderTypeAndTenantId(String providerType, String tenantId);

    Mono<MarketListingEntity> findBySeoSlugAndTenantId(String seoSlug, String tenantId);

    Mono<MarketListingEntity> findByQrCodeAndTenantId(String qrCode, String tenantId);

    Mono<Boolean> existsByProviderIdAndTenantId(UUID providerId, String tenantId);

    Mono<Long> countByTenantId(String tenantId);

    @Query("SELECT * FROM tnt_market.market_listings WHERE tenant_id = :tenantId AND average_rating >= :minRating")
    Flux<MarketListingEntity> findByMinRating(double minRating, String tenantId);

    @Query("SELECT *, (6371 * acos(cos(radians(:lat)) * cos(radians(coverage_center_lat)) * " +
           "cos(radians(coverage_center_lng) - radians(:lng)) + sin(radians(:lat)) * " +
           "sin(radians(coverage_center_lat)))) AS distance FROM tnt_market.market_listings " +
           "WHERE tenant_id = :tenantId AND (:serviceType IS NULL OR id IN " +
           "(SELECT listing_id FROM tnt_market.service_offers WHERE service_type = :serviceType)) " +
           "HAVING distance < :radiusKm ORDER BY distance")
    Flux<MarketListingEntity> findNearby(double lat, double lng, double radiusKm,
                                          String serviceType, String tenantId);
}
