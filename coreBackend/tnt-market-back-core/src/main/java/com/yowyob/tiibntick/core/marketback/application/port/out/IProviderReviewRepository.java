package com.yowyob.tiibntick.core.marketback.application.port.out;

import com.yowyob.tiibntick.core.marketback.domain.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Outbound port — ProviderReview persistence contract.
 * @author MANFOUO Braun
 */
public interface IProviderReviewRepository {
    Mono<ProviderReview> save(ProviderReview review);
    Mono<ProviderReview> findById(ReviewId id);
    Flux<ProviderReview> findByListingId(MarketListingId listingId);
    Flux<ProviderReview> findByClientId(UUID clientId, String tenantId);
    Flux<ProviderReview> findPublishedByListingId(MarketListingId listingId);
    Mono<ProviderReview> findByOrderId(MarketOrderId orderId);
    Flux<ProviderReview> findPendingModeration(String tenantId);
    Mono<Void> delete(ReviewId id);
}
