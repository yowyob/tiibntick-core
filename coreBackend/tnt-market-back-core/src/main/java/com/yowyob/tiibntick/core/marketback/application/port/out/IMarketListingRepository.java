package com.yowyob.tiibntick.core.marketback.application.port.out;

import com.yowyob.tiibntick.core.marketback.domain.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Outbound port — MarketListing persistence contract.
 * Implementations are in the persistence adapter layer.
 *
 * @author MANFOUO Braun
 */
public interface IMarketListingRepository {

    Mono<MarketListing> save(MarketListing listing);

    Mono<MarketListing> findById(MarketListingId id, String tenantId);

    Mono<MarketListing> findByProviderIdAndTenantId(UUID providerId, String tenantId);

    Flux<MarketListing> findByTenantId(String tenantId);

    Flux<MarketListing> findByStatus(ListingStatus status, String tenantId);

    Flux<MarketListing> findByProviderType(ProviderType providerType, String tenantId);

    Mono<MarketListing> findBySeoSlug(String slug, String tenantId);

    Mono<MarketListing> findByQrCode(String qrCode, String tenantId);

    Flux<MarketListing> findByMinRating(double minRating, String tenantId);

    Flux<MarketListing> findNearby(double lat, double lng, double radiusKm,
                                    ServiceType serviceType, String tenantId);

    Mono<Void> updateRating(MarketListingId id, String tenantId, double avg, long count);

    /** Returns the total count of listings for a given tenant (used by stats). */
    Mono<Long> countByTenantId(String tenantId);

    Mono<Boolean> existsByProviderIdAndTenantId(UUID providerId, String tenantId);

    Mono<Void> delete(MarketListingId id, String tenantId);
}
