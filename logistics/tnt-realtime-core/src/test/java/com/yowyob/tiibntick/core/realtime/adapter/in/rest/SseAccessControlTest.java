package com.yowyob.tiibntick.core.realtime.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.realtime.adapter.in.sse.SseController;
import com.yowyob.tiibntick.core.realtime.application.port.in.IWatchSubDeliverersUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.service.SseDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Live tracking streams are scoped to who may see them")
class SseAccessControlTest {

    private final SseDomainService sse = mock(SseDomainService.class);
    private final IWatchSubDeliverersUseCase fleet = mock(IWatchSubDeliverersUseCase.class);
    private final SseController controller = new SseController(sse, fleet, mock(IWebSocketBroadcaster.class));
    private final UUID tenant = UUID.randomUUID();
    private final UUID org = UUID.randomUUID();

    private TntUserIdentity identity(UUID organization, Set<String> permissions) {
        return new TntUserIdentity(UUID.randomUUID(), tenant, UUID.randomUUID(), organization, null, permissions, false);
    }

    private static void assertForbidden(Throwable e) {
        assertThat(e).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("following a parcel by tracking code uses the tenant of the token, not a header, and needs authentication")
    void trackingByCode() {
        when(sse.trackingStream(anyString(), anyString())).thenReturn(Flux.just("{}"));
        StepVerifier.create(controller.trackPackage(identity(null, Set.of()), "TNT-1").take(1))
                .assertNext(ev -> assertThat(ev.event()).isEqualTo("eta-update")).verifyComplete();
        verify(sse).trackingStream("TNT-1", tenant.toString());

        StepVerifier.create(controller.trackPackage(null, "TNT-1"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED)).verify();
    }

    @Test
    @DisplayName("the mission feed is for fleet supervisors only")
    void missionFeed() {
        StepVerifier.create(controller.trackMission(identity(null, Set.of("delivery:track")), "m1"))
                .expectErrorSatisfies(SseAccessControlTest::assertForbidden).verify();
        verifyNoInteractions(sse);

        when(sse.missionStream(anyString(), anyString())).thenReturn(Flux.just("{}"));
        StepVerifier.create(controller.trackMission(identity(null, Set.of("route:compute")), "m1").take(1))
                .assertNext(ev -> assertThat(ev.event()).isEqualTo("mission-update")).verifyComplete();
    }

    @Test
    @DisplayName("a fleet can be watched by its own organisation or a supervisor, never by another organisation")
    void fleetFeed() {
        StepVerifier.create(controller.streamFreelancerOrgFleet(identity(UUID.randomUUID(), Set.of()), org.toString()))
                .expectErrorSatisfies(SseAccessControlTest::assertForbidden).verify();
        StepVerifier.create(controller.streamFreelancerOrgFleet(identity(null, Set.of()), org.toString()))
                .expectErrorSatisfies(SseAccessControlTest::assertForbidden).verify();
        verifyNoInteractions(fleet);

        when(fleet.watchSubDeliverers(org.toString(), tenant.toString())).thenReturn(Flux.empty());
        StepVerifier.create(controller.streamFreelancerOrgFleet(identity(org, Set.of()), org.toString()))
                .expectComplete().verify(Duration.ofSeconds(2));
        StepVerifier.create(controller.streamFreelancerOrgFleet(identity(null, Set.of("route:compute")), org.toString()))
                .expectComplete().verify(Duration.ofSeconds(2));
        verify(fleet, times(2)).watchSubDeliverers(org.toString(), tenant.toString());
    }
}
