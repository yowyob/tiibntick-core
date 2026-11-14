package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AnnouncementUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.query.NearbyCriteria;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerWhereaboutsResolver;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@code GET /api/announcements/nearby} : d'où regarde le livreur.
 * Vrai contrôleur, vrai {@link FreelancerWhereaboutsResolver} (rayon par défaut 15 km) ; le filtre
 * SQL lui-même est prouvé par {@code NearbyAnnouncementsQueryIntegrationTest} (tnt-delivery-core).
 */
@DisplayName("route des annonces proches")
class NearbyAnnouncementsRouteTest {

    private final UUID caller = UUID.randomUUID();
    private final UUID baseAddress = UUID.randomUUID();
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final AddressUseCase addresses = mock(AddressUseCase.class);
    private final AnnouncementUseCase useCase = mock(AnnouncementUseCase.class);

    private WebTestClient client() {
        when(useCase.getNearbyAnnouncements(any())).thenReturn(Flux.just(new AnnouncementResponseDTO()));
        var controller = new AnnouncementController(useCase, new FreelancerWhereaboutsResolver(freelancers, addresses, 15));
        TntSecurityContext ctx = TntSecurityContext.builder().userId(caller).build();
        return WebTestClient.bindToController(controller)
                .argumentResolvers(cfg -> cfg.addCustomResolver(new Fixed(ctx)))
                .build();
    }

    private NearbyCriteria criteriaOf(String uri) {
        client().get().uri(uri).exchange().expectStatus().isOk().expectBodyList(Object.class).hasSize(1);
        ArgumentCaptor<NearbyCriteria> c = ArgumentCaptor.forClass(NearbyCriteria.class);
        verify(useCase).getNearbyAnnouncements(c.capture());
        return c.getValue();
    }

    private void storedProfile(Float lat, Float lng, String city) {
        GofpFreelancer f = GofpFreelancer.builder().id(caller).coreUserId(caller)
                .latitudeGps(lat).longitudeGps(lng)
                .operationalBaseAddressId(city != null ? baseAddress : null).build();
        when(freelancers.findByCoreUserId(caller)).thenReturn(Mono.just(f));
        if (city != null) {
            when(addresses.getAddressById(baseAddress)).thenReturn(Mono.just(AddressDTO.builder()
                    .address(Address.builder().street("rue").city(city).country("CM").build()).build()));
        }
    }

    @Test
    @DisplayName("paramètres explicites : ils gagnent, rayon par défaut 15 km")
    void explicitParams() {
        NearbyCriteria c = criteriaOf("/api/announcements/nearby?lat=4.05&lng=9.76&city=Douala");
        assertThat(c).isEqualTo(new NearbyCriteria(4.05, 9.76, 15, "Douala"));
        verifyNoInteractions(freelancers);
    }

    @Test
    @DisplayName("sans paramètre : dernière position GPS et ville de base du profil")
    void storedWhereabouts() {
        storedProfile(4.05f, 9.76f, "Douala");
        NearbyCriteria c = criteriaOf("/api/announcements/nearby");
        assertThat(c.latitude()).isCloseTo(4.05, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(c.longitude()).isCloseTo(9.76, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(c.city()).isEqualTo("Douala");
        assertThat(c.radiusKm()).isEqualTo(15);
    }

    @Test
    @DisplayName("rayon explicite respecté")
    void explicitRadius() {
        storedProfile(4.05f, 9.76f, null);
        assertThat(criteriaOf("/api/announcements/nearby?radiusKm=5").radiusKm()).isEqualTo(5);
    }

    @Test
    @DisplayName("livreur sans position ni ville connue : critères vides (→ toutes les annonces ouvertes)")
    void nothingKnown() {
        storedProfile(null, null, null);
        assertThat(criteriaOf("/api/announcements/nearby")).isEqualTo(new NearbyCriteria(null, null, 15, null));
    }

    @Test
    @DisplayName("pas de profil livreur du tout : critères vides aussi")
    void noProfile() {
        when(freelancers.findByCoreUserId(caller)).thenReturn(Mono.empty());
        when(freelancers.findById(caller)).thenReturn(Mono.empty());
        assertThat(criteriaOf("/api/announcements/nearby")).isEqualTo(new NearbyCriteria(null, null, 15, null));
    }

    @Test
    @DisplayName("« nearby » n'est pas pris pour un identifiant par GET /{id}")
    void nearbyIsNotAnId() {
        storedProfile(null, null, null);
        client().get().uri("/api/announcements/nearby").exchange().expectStatus().isOk();
        verify(useCase, never()).getAnnouncement(any());
    }

    static final class Fixed implements HandlerMethodArgumentResolver {
        private final TntSecurityContext ctx;
        Fixed(TntSecurityContext ctx) { this.ctx = ctx; }
        @Override public boolean supportsParameter(MethodParameter p) {
            return p.getParameterType().equals(TntSecurityContext.class);
        }
        @Override public Mono<Object> resolveArgument(MethodParameter p, BindingContext b, ServerWebExchange e) {
            return Mono.justOrEmpty(ctx);
        }
    }
}
