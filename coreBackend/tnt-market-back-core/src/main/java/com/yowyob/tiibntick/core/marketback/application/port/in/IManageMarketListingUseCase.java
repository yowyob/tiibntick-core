package com.yowyob.tiibntick.core.marketback.application.port.in;

import com.yowyob.tiibntick.core.marketback.application.port.in.command.*;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port — all use cases for MarketListing management.
 * @author MANFOUO Braun
 */
public interface IManageMarketListingUseCase {

    Mono<MarketListingResponse> createListing(CreateMarketListingCommand command);
    Mono<MarketListingResponse> updateListing(UUID listingId, UpdateMarketListingCommand command, String tenantId);
    Mono<MarketListingResponse> submitForReview(UUID listingId, String tenantId);
    Mono<MarketListingResponse> approveListing(UUID listingId, UUID adminId, String tenantId);
    Mono<MarketListingResponse> rejectListing(UUID listingId, UUID adminId, String reason, String tenantId);
    Mono<MarketListingResponse> unpublishListing(UUID listingId, String tenantId);
    Mono<MarketListingResponse> suspendListing(UUID listingId, String reason, String tenantId);
    Mono<MarketListingResponse> getListing(UUID listingId, String tenantId);
    Mono<MarketListingResponse> getListingBySeoSlug(String slug, String tenantId);
    Flux<MarketListingResponse> getListingsByProvider(UUID providerId, String tenantId);
    Flux<MarketListingResponse> getListingsPendingModeration(String tenantId);
    Mono<Void> trackView(UUID listingId, String tenantId);
    Mono<Void> deleteListing(UUID listingId, String tenantId);
}
