package com.yowyob.tiibntick.core.marketback.application.port.out;

import com.yowyob.tiibntick.core.marketback.application.port.in.query.MarketSearchQuery;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListing;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — indexes/updates MarketListing documents in Elasticsearch
 * via tnt-search-core.
 * @author MANFOUO Braun
 */
public interface IMarketSearchIndexPort {
    Mono<Void> indexListing(MarketListing listing);
    Mono<Void> removeListing(MarketListingId listingId);
    Mono<Void> updateRating(MarketListingId listingId, double newAvgRating, int totalReviews);
    Flux<UUID> searchListings(MarketSearchQuery query);
}
