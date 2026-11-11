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

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A geohash tile streams the live position of every deliverer in it: fleet supervisors only. */
class SseTileAccessTest {

    private final IWebSocketBroadcaster broadcaster = mock(IWebSocketBroadcaster.class);
    private final SseController controller = new SseController(
            mock(SseDomainService.class), mock(IWatchSubDeliverersUseCase.class), broadcaster);

    private static TntUserIdentity identity(Set<String> permissions) {
        return new TntUserIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null, permissions, false);
    }

    @Test
    @DisplayName("a client or deliverer cannot subscribe to a fleet tile")
    void ordinaryUserIsForbidden() {
        StepVerifier.create(controller.streamTile(identity(Set.of("geo:read", "route:read")), "u0"))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                })
                .verify();
        verifyNoInteractions(broadcaster);
    }

    @Test
    @DisplayName("a fleet supervisor (route:compute) receives the tile stream")
    void supervisorCanSubscribe() {
        when(broadcaster.subscribeToTopic(anyString())).thenReturn(Flux.just("payload"));
        StepVerifier.create(controller.streamTile(identity(Set.of("route:compute")), "u0"))
                .assertNext(ev -> assertThat(ev.event()).isEqualTo("tile-update"))
                .verifyComplete();
        verify(broadcaster).subscribeToTopic(anyString());
    }
}
