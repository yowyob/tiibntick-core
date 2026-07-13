package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.marketback.application.port.in.IMarketSearchUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.CompareProvidersQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.NearbySearchQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.PriceRangeSearchQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.RatingSearchQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.SearchOffersByTypeQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.MarketSearchQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketListingResponse;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.ProviderComparisonResponse;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.ServiceOfferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Generic Market discovery/search API — provider discovery, price comparison,
 * nearby relay points. Single entry point the Market BFF calls to power the
 * marketplace search screens.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Market Search", description = "Provider discovery, comparison and offer search")
@RestController
@RequestMapping("/api/v1/platform/market/search")
@RequiredArgsConstructor
public class MarketSearchController {

    private final IMarketSearchUseCase searchUseCase;

    @Operation(summary = "Search market listings")
    @GetMapping
    public Flux<MarketListingResponse> search(MarketSearchQuery query) {
        return searchUseCase.searchListings(query);
    }

    @Operation(summary = "Find nearby relay points")
    @GetMapping("/nearby")
    public Flux<MarketListingResponse> searchNearby(NearbySearchQuery query) {
        return searchUseCase.findNearbyRelayPoints(query);
    }

    @Operation(summary = "Search service offers by service type")
    @GetMapping("/offers/by-type")
    public Flux<ServiceOfferResponse> searchByType(SearchOffersByTypeQuery query) {
        return searchUseCase.searchOffersByServiceType(query);
    }

    @Operation(summary = "Search service offers by price range")
    @GetMapping("/offers/by-price")
    public Flux<ServiceOfferResponse> searchByPrice(PriceRangeSearchQuery query) {
        return searchUseCase.searchByPriceRange(query);
    }

    @Operation(summary = "Search listings by minimum rating")
    @GetMapping("/by-rating")
    public Flux<MarketListingResponse> searchByRating(RatingSearchQuery query) {
        return searchUseCase.searchByRating(query);
    }

    @Operation(summary = "Top listings for a given city")
    @GetMapping("/top-by-city")
    public Flux<MarketListingResponse> getTopByCity(
            @RequestParam String city,
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "10") int limit) {
        return searchUseCase.getTopListingsByCity(city, tenantId, limit);
    }

    @Operation(summary = "Find a listing by its QR code")
    @GetMapping("/qr/{qrCode}")
    public Mono<MarketListingResponse> findByQr(
            @PathVariable String qrCode,
            @RequestParam String tenantId) {
        return searchUseCase.findByQrCode(qrCode, tenantId);
    }

    @Operation(summary = "Compare multiple providers")
    @PostMapping("/compare")
    public Mono<ProviderComparisonResponse> compare(@RequestBody CompareProvidersQuery query) {
        return searchUseCase.compareProviders(query);
    }
}
