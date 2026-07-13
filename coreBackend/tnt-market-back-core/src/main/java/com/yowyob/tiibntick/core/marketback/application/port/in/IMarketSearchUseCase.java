package com.yowyob.tiibntick.core.marketback.application.port.in;

import com.yowyob.tiibntick.core.marketback.application.port.in.query.*;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Inbound port — Market discovery, search and comparison use cases.
 * @author MANFOUO Braun
 */
public interface IMarketSearchUseCase {

    Flux<MarketListingResponse> searchListings(MarketSearchQuery query);
    Flux<MarketListingResponse> findNearbyRelayPoints(NearbySearchQuery query);
    Mono<ProviderComparisonResponse> compareProviders(CompareProvidersQuery query);
    Flux<ServiceOfferResponse> searchOffersByServiceType(SearchOffersByTypeQuery query);
    Flux<ServiceOfferResponse> searchByPriceRange(PriceRangeSearchQuery query);
    Flux<MarketListingResponse> searchByRating(RatingSearchQuery query);
    Flux<MarketListingResponse> getTopListingsByCity(String city, String tenantId, int limit);
    Mono<MarketListingResponse> findByQrCode(String qrCode, String tenantId);
}
