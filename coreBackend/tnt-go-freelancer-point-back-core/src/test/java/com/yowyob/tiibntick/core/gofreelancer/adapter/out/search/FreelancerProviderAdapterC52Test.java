package com.yowyob.tiibntick.core.gofreelancer.adapter.out.search;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.FreelancerDocument;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase.FreelancerCandidate;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Lot C-52 — which couriers the proximity search returns. */
class FreelancerProviderAdapterC52Test {

    // Douala, Akwa
    private static final double LAT = 4.0511;
    private static final double LNG = 9.7679;

    private final GofpFreelancerRepository repo = mock(GofpFreelancerRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<FreelancerSearchRepository> provider = mock(ObjectProvider.class);
    private final FreelancerProviderAdapter adapter = new FreelancerProviderAdapter(repo, provider);

    private static GofpFreelancer courier(Float lat, Float lng, String availability) {
        return GofpFreelancer.builder().id(UUID.randomUUID()).isActive(true)
                .latitudeGps(lat).longitudeGps(lng).availabilityStatus(availability)
                .remainingDeliveries(100).build();
    }

    private List<UUID> ids(List<FreelancerCandidate> l) {
        return l.stream().map(FreelancerCandidate::getFreelancerId).toList();
    }

    @Test
    @DisplayName("positionné à < 5 km → présent ; sans position → absent ; en pause → absent ; loin → absent")
    void sql_positionedNearOnly_notPaused() {
        GofpFreelancer near = courier(4.06f, 9.77f, "AVAILABLE");      // ~1 km
        GofpFreelancer nearNullStatus = courier(4.04f, 9.76f, null);     // default = available
        GofpFreelancer noPosition = courier(null, null, "AVAILABLE");
        GofpFreelancer paused = courier(4.05f, 9.77f, "PAUSED");
        GofpFreelancer far = courier(3.87f, 11.52f, "AVAILABLE");        // Yaoundé
        when(provider.getIfAvailable()).thenReturn(null);
        when(repo.findAllByIsActive(true)).thenReturn(Flux.just(near, nearNullStatus, noPosition, paused, far));

        List<FreelancerCandidate> out = adapter.findActiveCandidatesNear(LAT, LNG, 5).block();

        assertThat(ids(out)).containsExactlyInAnyOrder(near.getId(), nearNullStatus.getId());
    }

    @Test
    @DisplayName("ES actif mais ne connaît pas le livreur positionné en SQL → il est quand même proposé")
    void es_doesNotHideSqlPositionedCourier() {
        GofpFreelancer sqlOnly = courier(4.06f, 9.77f, "AVAILABLE");
        UUID esOnly = UUID.randomUUID();
        FreelancerDocument doc = FreelancerDocument.builder().id(esOnly).location(new GeoPoint(4.05, 9.77)).build();
        FreelancerSearchRepository es = mock(FreelancerSearchRepository.class);
        when(provider.getIfAvailable()).thenReturn(es);
        when(es.findByIsAvailableTrueAndIsActiveTrueAndLocationNear(any(), any())).thenReturn(Flux.just(doc));
        when(repo.findAllByIsActive(true)).thenReturn(Flux.just(sqlOnly));
        when(repo.findById(esOnly)).thenReturn(Mono.empty());
        when(repo.findByCoreFreelancerId(esOnly)).thenReturn(Mono.empty());

        List<FreelancerCandidate> out = adapter.findActiveCandidatesNear(LAT, LNG, 5).block();

        assertThat(ids(out)).containsExactlyInAnyOrder(sqlOnly.getId(), esOnly);
    }

    @Test
    @DisplayName("ES renvoie un livreur en pause dans SQL → écarté")
    void es_pausedInSql_excluded() {
        GofpFreelancer paused = courier(4.06f, 9.77f, "PAUSED");
        FreelancerDocument doc = FreelancerDocument.builder().id(paused.getId()).location(new GeoPoint(4.06, 9.77)).build();
        FreelancerSearchRepository es = mock(FreelancerSearchRepository.class);
        when(provider.getIfAvailable()).thenReturn(es);
        when(es.findByIsAvailableTrueAndIsActiveTrueAndLocationNear(any(), any())).thenReturn(Flux.just(doc));
        when(repo.findAllByIsActive(true)).thenReturn(Flux.just(paused));
        when(repo.findById(paused.getId())).thenReturn(Mono.just(paused));

        assertThat(adapter.findActiveCandidatesNear(LAT, LNG, 5).block()).isEmpty();
    }

    @Test
    @DisplayName("ES en panne → SQL seul")
    void es_down_sqlOnly() {
        GofpFreelancer near = courier(4.06f, 9.77f, null);
        FreelancerSearchRepository es = mock(FreelancerSearchRepository.class);
        when(provider.getIfAvailable()).thenReturn(es);
        when(es.findByIsAvailableTrueAndIsActiveTrueAndLocationNear(any(), any()))
                .thenReturn(Flux.error(new IllegalStateException("down")));
        when(repo.findAllByIsActive(true)).thenReturn(Flux.just(near));

        assertThat(ids(adapter.findActiveCandidatesNear(LAT, LNG, 5).block())).containsExactly(near.getId());
    }
}
