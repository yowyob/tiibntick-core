package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.ratelimit.NearbyRateLimiter;
import com.yowyob.tiibntick.core.linkback.application.port.in.ActivateBeaconUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.EndorseNodeUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.GetNetworkNodeProfileUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryNetworkNodesUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryTrustLinksUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.RegisterNetworkNodeUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.UpdateNodeLocationUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.UpdateNodeStatusUseCase;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import com.yowyob.tiibntick.core.linkback.domain.model.NodeRefType;
import com.yowyob.tiibntick.core.linkback.domain.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * Verifies the Phase 0 stop-gap on {@code NetworkNodeController#nearby} (audit n6
 * S25, Chantier G — see docs/audits/remediation/phase-0-critical.md): the endpoint
 * must reject the call with HTTP 429 when {@link NearbyRateLimiter} denies it, and
 * proceed normally otherwise.
 *
 * @author Dilane PAFE
 */
@ExtendWith(MockitoExtension.class)
class NetworkNodeControllerTest {

    @Mock RegisterNetworkNodeUseCase registerUseCase;
    @Mock UpdateNodeStatusUseCase updateStatusUseCase;
    @Mock UpdateNodeLocationUseCase updateLocationUseCase;
    @Mock QueryNetworkNodesUseCase queryUseCase;
    @Mock GetNetworkNodeProfileUseCase profileUseCase;
    @Mock ActivateBeaconUseCase beaconUseCase;
    @Mock EndorseNodeUseCase endorseNodeUseCase;
    @Mock QueryTrustLinksUseCase queryTrustLinksUseCase;
    @Mock NearbyRateLimiter nearbyRateLimiter;

    @InjectMocks NetworkNodeController controller;

    WebTestClient webTestClient;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
                .bindToController(controller)
                .controllerAdvice(new LinkBackExceptionHandler())
                .argumentResolvers(resolvers -> resolvers.addCustomResolver(new CurrentUserArgumentResolver()))
                .build();
    }

    static class CurrentUserArgumentResolver implements org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Mono<Object> resolveArgument(
                org.springframework.core.MethodParameter parameter,
                org.springframework.web.reactive.BindingContext bindingContext,
                org.springframework.web.server.ServerWebExchange exchange) {
            return Mono.just(new TntUserIdentity(
                    USER_ID, TENANT_ID, UUID.randomUUID(), null, null,
                    Set.of("link:read", "link:write"), false));
        }
    }

    @Test
    @DisplayName("GET /nearby returns 200 with results when the rate limiter allows the call")
    void nearbyReturnsResultsWhenAllowed() {
        when(nearbyRateLimiter.tryAcquire(any(), any(), any())).thenReturn(Mono.just(true));
        when(queryUseCase.findWithinBoundingBox(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Flux.just(buildTestNode()));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/platform/link/nodes/nearby")
                        .queryParam("minLat", "0.0")
                        .queryParam("minLng", "0.0")
                        .queryParam("maxLat", "1.0")
                        .queryParam("maxLng", "1.0")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    @DisplayName("GET /nearby returns 429 when the rate limiter denies the call, without querying the repository")
    void nearbyReturns429WhenThrottled() {
        when(nearbyRateLimiter.tryAcquire(any(), any(), any())).thenReturn(Mono.just(false));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/platform/link/nodes/nearby")
                        .queryParam("minLat", "0.0")
                        .queryParam("minLng", "0.0")
                        .queryParam("maxLat", "1.0")
                        .queryParam("maxLng", "1.0")
                        .build())
                .exchange()
                .expectStatus().isEqualTo(429);
    }

    private NetworkNode buildTestNode() {
        Instant now = Instant.now();
        return NetworkNode.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .refType(NodeRefType.AGENCY)
                .refId(UUID.randomUUID())
                .status(NodeStatus.ONLINE)
                .trustScore(0)
                .gamificationLevel(1)
                .communityScore(0)
                .lastKnownLocation(GeoPoint.of(0.5, 0.5))
                .badges(Set.of())
                .zoneTransitionCount(0)
                .polVerified(false)
                .polPeerCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
