package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.RelayPointVisualsRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IOpeningHoursRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointAccessGuard;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointHoursService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.OpeningHours;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointVisuals;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot C-49 — the owner of a relay point saves its opening hours and reads them and its photos back. */
@DisplayName("My relay point — opening hours and photos")
class RelayPointMineDetailsControllerTest {

    static final UUID OWNER_USER = UUID.fromString("11111111-0000-0000-0000-000000000149");
    static final UUID OWNER_FREELANCER = UUID.fromString("22222222-0000-0000-0000-000000000149");
    static final UUID NOBODY_USER = UUID.fromString("33333333-0000-0000-0000-000000000149");
    static final UUID HUB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000149");

    private final GofpRelayPointRepository relayPoints = mock(GofpRelayPointRepository.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final IOpeningHoursRepository hoursRepo = mock(IOpeningHoursRepository.class);
    private final RelayPointVisualsRepository visualsRepo = mock(RelayPointVisualsRepository.class);
    private final List<OpeningHours> stored = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        when(freelancers.findByCoreUserId(any())).thenReturn(Mono.empty());
        when(freelancers.findByCoreUserId(OWNER_USER)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(OWNER_FREELANCER).coreFreelancerId(OWNER_FREELANCER).coreUserId(OWNER_USER).build()));
        when(relayPoints.findAllByCoreFreelancerId(any())).thenReturn(Flux.empty());
        when(relayPoints.findAllByCoreFreelancerId(OWNER_FREELANCER)).thenAnswer(i -> Flux.just(GofpRelayPoint.builder()
                .id(UUID.randomUUID()).coreRelayPointId(HUB).coreFreelancerId(OWNER_FREELANCER)
                .status(RelayPointStatus.PENDING).createdAt(Instant.now()).build()));
        when(hoursRepo.deleteAllByRelayPointId(HUB)).thenAnswer(i -> Mono.fromRunnable(stored::clear));
        when(hoursRepo.save(any())).thenAnswer(i -> {
            OpeningHours h = i.getArgument(0);
            stored.add(h);
            return Mono.just(h);
        });
        when(hoursRepo.findAllByRelayPointId(HUB)).thenAnswer(i -> Flux.fromIterable(List.copyOf(stored)));
        when(visualsRepo.findByCoreRelayPointId(HUB)).thenReturn(Mono.just(RelayPointVisuals.builder()
                .id(UUID.randomUUID()).coreRelayPointId(HUB).storefrontPhotoUrl("/uploads/front.jpg").build()));
    }

    private WebTestClient as(UUID userId) {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(userId).tenantId(UUID.randomUUID())
                .authenticated(true).roles(Set.of()).build();
        RelayPointAccessGuard guard = new RelayPointAccessGuard(relayPoints, freelancers);
        return WebTestClient.bindToController(
                        new RelayPointMineDetailsController(guard, new RelayPointHoursService(hoursRepo, visualsRepo)))
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new RelayPointAccessControllerTest.FixedContextResolver(ctx)))
                .build();
    }

    @Test
    @DisplayName("the owner saves the week and reads it back, keyed by the hub id, closed days without times")
    void ownerSavesAndReadsBack() {
        as(OWNER_USER).put().uri("/api/v1/gofp/relay-point-profiles/mine/opening-hours")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[{\"dayOfWeek\":\"MONDAY\",\"openTime\":\"08:00\",\"closeTime\":\"18:00\"},"
                        + "{\"dayOfWeek\":\"SUNDAY\",\"isClosed\":true,\"openTime\":\"09:00\",\"closeTime\":\"10:00\"}]")
                .exchange().expectStatus().isOk();
        assertThat(stored).hasSize(2).allMatch(h -> HUB.equals(h.getRelayPointId()));
        assertThat(stored).filteredOn(h -> Boolean.TRUE.equals(h.getIsClosed()))
                .singleElement().matches(h -> h.getOpenTime() == null && h.getCloseTime() == null);

        as(OWNER_USER).get().uri("/api/v1/gofp/relay-point-profiles/mine/opening-hours")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].dayOfWeek").isEqualTo("MONDAY");
    }

    @Test
    @DisplayName("a day twice, or an open day closing before it opens, is refused and nothing is replaced")
    void invalidWeek_isRefused() {
        as(OWNER_USER).put().uri("/api/v1/gofp/relay-point-profiles/mine/opening-hours")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[{\"dayOfWeek\":\"MONDAY\",\"openTime\":\"18:00\",\"closeTime\":\"08:00\"}]")
                .exchange().expectStatus().isBadRequest();
        as(OWNER_USER).put().uri("/api/v1/gofp/relay-point-profiles/mine/opening-hours")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[{\"dayOfWeek\":\"MONDAY\",\"isClosed\":true},{\"dayOfWeek\":\"MONDAY\",\"isClosed\":true}]")
                .exchange().expectStatus().isBadRequest();
        verify(hoursRepo, never()).deleteAllByRelayPointId(any());
    }

    @Test
    @DisplayName("the owner reads the photos of their relay point back")
    void ownerReadsVisuals() {
        as(OWNER_USER).get().uri("/api/v1/gofp/relay-point-profiles/mine/visuals")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.storefrontPhotoUrl").isEqualTo("/uploads/front.jpg");
    }

    @Test
    @DisplayName("a caller without a relay point gets 404 and writes nothing")
    void noRelayPoint_404() {
        as(NOBODY_USER).get().uri("/api/v1/gofp/relay-point-profiles/mine/opening-hours")
                .exchange().expectStatus().isNotFound();
        as(NOBODY_USER).put().uri("/api/v1/gofp/relay-point-profiles/mine/opening-hours")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("[]")
                .exchange().expectStatus().isNotFound();
        verify(hoursRepo, never()).save(any());
    }
}
