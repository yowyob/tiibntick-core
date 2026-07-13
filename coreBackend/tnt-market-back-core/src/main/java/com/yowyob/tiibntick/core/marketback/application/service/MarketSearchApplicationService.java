package com.yowyob.tiibntick.core.marketback.application.service;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.marketback.application.port.in.IMarketSearchUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.CompareProvidersQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.MarketSearchQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.NearbySearchQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.PriceRangeSearchQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.RatingSearchQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.query.SearchOffersByTypeQuery;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketListingResponse;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.ProviderComparisonResponse;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.ServiceOfferResponse;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketListingRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketSearchIndexPort;
import com.yowyob.tiibntick.core.marketback.domain.exception.ListingNotFoundException;
import com.yowyob.tiibntick.core.marketback.domain.model.CoverageZone;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListing;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Application service — Market discovery/search use cases.
 *
 * <p>Implements the listing-scoped members of {@link IMarketSearchUseCase} on top of
 * the same outbound ports {@link MarketListingApplicationService} already uses
 * ({@link IMarketListingRepository}, {@link IMarketSearchIndexPort}) — no new port
 * abstraction was introduced.</p>
 *
 * <p>{@link #findNearbyRelayPoints(NearbySearchQuery)} re-ranks the SQL-prefiltered
 * candidates with tnt-geo-core's {@link GeoPoint#haversineDistanceTo(GeoPoint)} rather
 * than trusting the R2DBC query's row order, since the repository's {@code findNearby}
 * only guarantees a coarse radius filter (see
 * {@code R2dbcMarketListingRepository#findNearby}).</p>
 *
 * <p>TODO(market-migration): {@link #searchOffersByServiceType}, {@link #searchByPriceRange}
 * and {@link #compareProviders} genuinely need the ServiceOffer aggregate's outbound
 * port (price/availability data), which is out of scope here — that aggregate's
 * service is being wired concurrently by another engineer. Left unimplemented
 * (fail loudly) rather than faked.</p>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSearchApplicationService implements IMarketSearchUseCase {

    private final IMarketListingRepository listingRepository;
    private final IMarketSearchIndexPort searchIndexPort;

    @Override
    public Flux<MarketListingResponse> searchListings(MarketSearchQuery query) {
        return searchIndexPort.searchListings(query)
                .flatMap(listingId -> listingRepository.findById(MarketListingId.of(listingId), query.tenantId()))
                .map(this::toResponse);
    }

    @Override
    public Flux<MarketListingResponse> findNearbyRelayPoints(NearbySearchQuery query) {
        return Flux.defer(() -> {
            GeoPoint origin = GeoPoint.of(query.lat(), query.lng());
            return listingRepository.findNearby(query.lat(), query.lng(), query.radiusKm(), null, query.tenantId())
                    .filter(listing -> listing.getCoverageZone() != null
                            && listing.getCoverageZone().centerLat() != null
                            && listing.getCoverageZone().centerLng() != null)
                    .map(listing -> {
                        CoverageZone zone = listing.getCoverageZone();
                        double distanceKm = origin.haversineDistanceTo(GeoPoint.of(zone.centerLat(), zone.centerLng()));
                        return Map.entry(listing, distanceKm);
                    })
                    .sort(Comparator.comparingDouble(entry -> entry.getValue()))
                    .take(Math.max(query.limit(), 0))
                    .map(entry -> toResponse(entry.getKey()));
        });
    }

    @Override
    public Mono<ProviderComparisonResponse> compareProviders(CompareProvidersQuery query) {
        // TODO(market-migration): needs ServiceOffer pricing/ETA data (basePriceXaf,
        // perKmRateXaf, perKgRateXaf) to compute ProviderComparisonItem.estimatedPriceXaf
        // /etaHours — out of scope of the MarketListing-focused integration pass;
        // ServiceOffer's outbound port is owned by the engineer wiring that aggregate.
        return Mono.error(new UnsupportedOperationException(
                "compareProviders requires ServiceOffer pricing data — not yet wired"));
    }

    @Override
    public Flux<ServiceOfferResponse> searchOffersByServiceType(SearchOffersByTypeQuery query) {
        // TODO(market-migration): needs a ServiceOffer repository — out of scope here.
        return Flux.error(new UnsupportedOperationException(
                "searchOffersByServiceType requires the ServiceOffer aggregate — not yet wired"));
    }

    @Override
    public Flux<ServiceOfferResponse> searchByPriceRange(PriceRangeSearchQuery query) {
        // TODO(market-migration): needs a ServiceOffer repository — out of scope here.
        return Flux.error(new UnsupportedOperationException(
                "searchByPriceRange requires the ServiceOffer aggregate — not yet wired"));
    }

    @Override
    public Flux<MarketListingResponse> searchByRating(RatingSearchQuery query) {
        return listingRepository.findByMinRating(query.minRating(), query.tenantId())
                .filter(listing -> query.city() == null || query.city().isBlank()
                        || (listing.getCoverageZone() != null && listing.getCoverageZone().containsCity(query.city())))
                .skip((long) Math.max(query.page(), 0) * Math.max(query.size(), 0))
                .take(Math.max(query.size(), 0))
                .map(this::toResponse);
    }

    @Override
    public Flux<MarketListingResponse> getTopListingsByCity(String city, String tenantId, int limit) {
        return listingRepository.findByTenantId(tenantId)
                .filter(listing -> listing.getCoverageZone() != null && listing.getCoverageZone().containsCity(city))
                .sort(Comparator.comparingDouble(MarketListing::getAverageRating).reversed())
                .take(Math.max(limit, 0))
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketListingResponse> findByQrCode(String qrCode, String tenantId) {
        return listingRepository.findByQrCode(qrCode, tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException("qrCode=" + qrCode)))
                .map(this::toResponse);
    }

    // -------------------------------------------------------
    // Mapper (mirrors MarketListingApplicationService#toResponse)
    // -------------------------------------------------------

    private MarketListingResponse toResponse(MarketListing l) {
        CoverageZone zone = l.getCoverageZone();
        return new MarketListingResponse(
                l.getId().value(),
                l.getTenantId(),
                l.getProviderId(),
                l.getProviderType(),
                l.getStatus(),
                l.getVitrine().displayName(),
                l.getVitrine().tagline(),
                l.getVitrine().description(),
                l.getVitrine().logoKey(),
                l.getVitrine().bannerKey(),
                l.getVitrine().contactPhone(),
                l.getVitrine().contactEmail(),
                l.getVitrine().websiteUrl(),
                zone != null ? zone.cities() : List.of(),
                zone != null ? zone.radiusKm() : null,
                zone != null ? zone.centerLat() : null,
                zone != null ? zone.centerLng() : null,
                l.getSeoSlug(),
                l.getAverageRating(),
                l.getTotalReviews(),
                l.getViewCount(),
                l.getConversionCount(),
                l.getPublishedAt(),
                l.getCreatedAt(),
                l.getUpdatedAt());
    }
}
