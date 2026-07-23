package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.ratelimit.NearbyRateLimiter;
import com.yowyob.tiibntick.core.linkback.application.port.in.ConfirmNetworkAlertUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryNetworkAlertsUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.ReportNetworkAlertUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.ResolveNetworkAlertUseCase;
import com.yowyob.tiibntick.core.linkback.domain.model.AlertSeverity;
import com.yowyob.tiibntick.core.linkback.domain.model.AlertStatus;
import com.yowyob.tiibntick.core.linkback.domain.model.AlertType;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkAlert;
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
 * Verifies the Phase 0 stop-gap on {@code NetworkAlertController#nearby} (audit n6
 * S25, Chantier G — see docs/audits/remediation/phase-0-critical.md): the endpoint
 * must reject the call with HTTP 429 when {@link NearbyRateLimiter} denies it, and
 * proceed normally otherwise.
 *
 * @author Dilane PAFE
 */
@ExtendWith(MockitoExtension.class)
class NetworkAlertControllerTest {

    @Mock ReportNetworkAlertUseCase reportUseCase;
    @Mock ConfirmNetworkAlertUseCase confirmUseCase;
    @Mock ResolveNetworkAlertUseCase resolveUseCase;
    @Mock QueryNetworkAlertsUseCase queryUseCase;
    @Mock NearbyRateLimiter nearbyRateLimiter;

    @InjectMocks NetworkAlertController controller;

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
        when(queryUseCase.findActiveNearby(any(), any(), anyDouble()))
                .thenReturn(Flux.just(buildTestAlert()));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/platform/link/alerts/nearby")
                        .queryParam("lat", "0.5")
                        .queryParam("lng", "0.5")
                        .queryParam("radiusKm", "5")
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
                .uri(uriBuilder -> uriBuilder.path("/api/v1/platform/link/alerts/nearby")
                        .queryParam("lat", "0.5")
                        .queryParam("lng", "0.5")
                        .queryParam("radiusKm", "5")
                        .build())
                .exchange()
                .expectStatus().isEqualTo(429);
    }

    private NetworkAlert buildTestAlert() {
        Instant now = Instant.now();
        return NetworkAlert.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .reporterId(UUID.randomUUID())
                .type(AlertType.POTHOLE)
                .description("Test pothole")
                .location(GeoPoint.of(0.5, 0.5))
                .severity(AlertSeverity.LOW)
                .status(AlertStatus.ACTIVE)
                .confirmCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
