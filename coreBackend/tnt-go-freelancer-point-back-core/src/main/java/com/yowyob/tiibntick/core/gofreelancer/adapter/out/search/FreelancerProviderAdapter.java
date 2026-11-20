package com.yowyob.tiibntick.core.gofreelancer.adapter.out.search;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.FreelancerDocument;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IFreelancerProviderPort;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase.FreelancerCandidate;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Best-effort freelancer candidate provider: Elasticsearch geo-near when available,
 * otherwise SQL fallback over active {@link GofpFreelancer} rows with GPS.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreelancerProviderAdapter implements IFreelancerProviderPort {

    private final GofpFreelancerRepository gofpFreelancerRepository;
    private final ObjectProvider<FreelancerSearchRepository> searchRepositoryProvider;

    /**
     * Lot C-52 — SQL is the source of truth for positions: the only writer of a courier's position
     * ({@code CourierPresenceService}) updates {@code gofp_freelancers}, and nothing in this module
     * indexes {@link FreelancerDocument}s. Elasticsearch results, when the search is enabled, are
     * therefore merged with the SQL ones instead of replacing them — otherwise a courier positioned
     * in SQL but unknown to the index would be missed as soon as the index returns anyone. Paused
     * couriers are left out on both paths.
     */
    @Override
    public Mono<List<FreelancerCandidate>> findActiveCandidatesNear(
            double latitude, double longitude, double radiusKm) {

        FreelancerSearchRepository searchRepo = searchRepositoryProvider.getIfAvailable();
        Mono<List<FreelancerCandidate>> sql = sqlFallback(latitude, longitude, radiusKm);
        if (searchRepo == null) {
            return sql;
        }
        Mono<List<FreelancerCandidate>> es = searchRepo
                .findByIsAvailableTrueAndIsActiveTrueAndLocationNear(
                        new GeoPoint(latitude, longitude),
                        new Distance(radiusKm, Metrics.KILOMETERS))
                .map(this::fromDocument)
                .filterWhen(this::notPausedInSql)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("[FreelancerProvider] ES lookup failed ({}), SQL only", e.getMessage());
                    return Mono.just(List.of());
                });
        return Mono.zip(sql, es).map(t -> {
            java.util.Map<java.util.UUID, FreelancerCandidate> merged = new java.util.LinkedHashMap<>();
            t.getT1().forEach(c -> merged.put(c.getFreelancerId(), c));
            t.getT2().forEach(c -> merged.putIfAbsent(c.getFreelancerId(), c));
            return List.copyOf(merged.values());
        });
    }

    /** An index hit is kept unless its SQL row says the courier paused (unknown row → kept). */
    private Mono<Boolean> notPausedInSql(FreelancerCandidate c) {
        return gofpFreelancerRepository.findById(c.getFreelancerId())
                .switchIfEmpty(Mono.defer(() -> gofpFreelancerRepository.findByCoreFreelancerId(c.getFreelancerId())))
                .map(f -> !f.isPaused())
                .defaultIfEmpty(true)
                .onErrorReturn(true);
    }

    private Mono<List<FreelancerCandidate>> sqlFallback(
            double latitude, double longitude, double radiusKm) {
        return gofpFreelancerRepository.findAllByIsActive(true)
                .filter(f -> f.getLatitudeGps() != null && f.getLongitudeGps() != null)
                .filter(f -> !f.isPaused())
                .map(this::fromGofp)
                .filter(c -> haversine(c.getLatitude(), c.getLongitude(), latitude, longitude) <= radiusKm)
                .collectList()
                .doOnNext(list -> log.info(
                        "[FreelancerProvider] SQL returned {} candidates within {}km",
                        list.size(), radiusKm));
    }

    private FreelancerCandidate fromDocument(FreelancerDocument doc) {
        double lat = doc.getLocation() != null ? doc.getLocation().getLat() : 0.0;
        double lon = doc.getLocation() != null ? doc.getLocation().getLon() : 0.0;
        return FreelancerCandidate.builder()
                .freelancerId(doc.getId())
                .latitude(lat)
                .longitude(lon)
                .vehicleType(doc.getVehicleType())
                .rating(0.0)
                .build();
    }

    private FreelancerCandidate fromGofp(GofpFreelancer f) {
        return FreelancerCandidate.builder()
                .freelancerId(f.getId())
                .latitude(f.getLatitudeGps().doubleValue())
                .longitude(f.getLongitudeGps().doubleValue())
                .rating(f.getRating() != null ? f.getRating() : 0.0)
                .remainingDeliveries(f.getRemainingDeliveries())
                .totalDeliveries(f.getTotalDeliveries())
                .build();
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
