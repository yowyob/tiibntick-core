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

    @Override
    public Mono<List<FreelancerCandidate>> findActiveCandidatesNear(
            double latitude, double longitude, double radiusKm) {

        FreelancerSearchRepository searchRepo = searchRepositoryProvider.getIfAvailable();
        if (searchRepo != null) {
            return searchRepo
                    .findByIsAvailableTrueAndIsActiveTrueAndLocationNear(
                            new GeoPoint(latitude, longitude),
                            new Distance(radiusKm, Metrics.KILOMETERS))
                    .map(this::fromDocument)
                    .collectList()
                    .flatMap(list -> {
                        if (!list.isEmpty()) {
                            return Mono.just(list);
                        }
                        log.debug("[FreelancerProvider] ES returned empty — falling back to SQL");
                        return sqlFallback(latitude, longitude, radiusKm);
                    })
                    .onErrorResume(e -> {
                        log.warn("[FreelancerProvider] ES lookup failed ({}), SQL fallback", e.getMessage());
                        return sqlFallback(latitude, longitude, radiusKm);
                    });
        }
        return sqlFallback(latitude, longitude, radiusKm);
    }

    private Mono<List<FreelancerCandidate>> sqlFallback(
            double latitude, double longitude, double radiusKm) {
        return gofpFreelancerRepository.findAllByIsActive(true)
                .filter(f -> f.getLatitudeGps() != null && f.getLongitudeGps() != null)
                .map(this::fromGofp)
                .filter(c -> haversine(c.getLatitude(), c.getLongitude(), latitude, longitude) <= radiusKm)
                .collectList()
                .doOnNext(list -> log.info(
                        "[FreelancerProvider] SQL fallback returned {} candidates within {}km",
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
