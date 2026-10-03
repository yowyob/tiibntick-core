package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.application.service.GofpFreelancerProjectionService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpProvisioningProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.server.PathContainer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lot C-18 test 6 (best-effort) and lot C-19.1 (cost bounds) for
 * {@link GofpFreelancerProvisioningFilter}:
 * <ul>
 *   <li>routes outside the freelancer journey never touch the projection, the tenant
 *       resolver or the security context;</li>
 *   <li>a projected freelancer is looked up once, then served from the bounded cache;</li>
 *   <li>a non-freelancer is not cached (a profile created later is projected at once);</li>
 *   <li>a slow projection is cut at {@code projection-timeout} and the request proceeds.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GofpFreelancerProvisioningFilterTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String GOFP_ROUTE = "/api/announcements/e2ebb000-0000-0000-0000-e2ebb0000099/subscribe";

    @Mock private GofpFreelancerProjectionService projectionService;
    @Mock private TenantContextHolder              tenantContextHolder;
    @Mock private WebFilterChain                   chain;

    private GofpFreelancerProvisioningFilter filter;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        GofpProvisioningProperties props = new GofpProvisioningProperties();
        props.setProjectionTimeout(Duration.ofMillis(200));
        filter = new GofpFreelancerProvisioningFilter(projectionService, tenantContextHolder, registry, props);
        filter.init();
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT_ID));
    }

    // ── C-19.1 axe 1 : prédicat de chemin ─────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"/api/delivery-needs/user/42", "/actuator/health", "/api/v1/freelancers/me",
            "/api/v1/deliveries/freelancer", "/api/announcementsX"})
    void filter_routeOutsideFreelancerJourney_noLookupAtAll(String path) {
        UUID sub = UUID.randomUUID();

        StepVerifier.create(filter.filter(exchange(path), chain).contextWrite(securityCtx(sub.toString())))
                .verifyComplete();

        verifyNoInteractions(projectionService, tenantContextHolder);
        verify(chain).filter(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/announcements", "/api/announcements/123/subscribe", "/api/freelancers/123/policies",
            "/api/v1/gofp/freelancer-profiles/by-core-user/123"})
    void requiresProjection_freelancerJourneyRoutes(String path) {
        assertThat(GofpFreelancerProvisioningFilter.requiresProjection(PathContainer.parsePath(path))).isTrue();
    }

    // ── C-19.1 axe 2 : cache positif borné ───────────────────────────────

    @Test
    void filter_projectedFreelancer_secondRequestServedFromCache() {
        UUID sub = UUID.randomUUID();
        when(projectionService.projectIfAbsent(sub, TENANT_ID)).thenReturn(Mono.just(true));

        for (int i = 0; i < 3; i++) {
            StepVerifier.create(filter.filter(exchange(GOFP_ROUTE), chain).contextWrite(securityCtx(sub.toString())))
                    .verifyComplete();
        }

        verify(projectionService, times(1)).projectIfAbsent(sub, TENANT_ID);
        verify(chain, times(3)).filter(any());
    }

    @Test
    void filter_nonFreelancer_notCached_projectedAsSoonAsProfileExists() {
        UUID sub = UUID.randomUUID();
        when(projectionService.projectIfAbsent(sub, TENANT_ID))
                .thenReturn(Mono.just(false))   // pas encore freelancer
                .thenReturn(Mono.just(true));   // profil créé entre-temps

        StepVerifier.create(filter.filter(exchange(GOFP_ROUTE), chain).contextWrite(securityCtx(sub.toString())))
                .verifyComplete();
        StepVerifier.create(filter.filter(exchange(GOFP_ROUTE), chain).contextWrite(securityCtx(sub.toString())))
                .verifyComplete();

        verify(projectionService, times(2)).projectIfAbsent(sub, TENANT_ID);
    }

    @Test
    void filter_failedProjection_notCached_retriedOnNextRequest() {
        UUID sub = UUID.randomUUID();
        when(projectionService.projectIfAbsent(sub, TENANT_ID))
                .thenReturn(Mono.error(new RuntimeException("DB unavailable")))
                .thenReturn(Mono.just(true));

        StepVerifier.create(filter.filter(exchange(GOFP_ROUTE), chain).contextWrite(securityCtx(sub.toString())))
                .verifyComplete();
        StepVerifier.create(filter.filter(exchange(GOFP_ROUTE), chain).contextWrite(securityCtx(sub.toString())))
                .verifyComplete();

        verify(projectionService, times(2)).projectIfAbsent(sub, TENANT_ID);
    }

    // ── C-19.1 axe 3 : attente bornée ─────────────────────────────────────

    @Test
    void filter_slowProjection_cutAtTimeout_requestProceeds_failureCounted() {
        UUID sub = UUID.randomUUID();
        when(projectionService.projectIfAbsent(sub, TENANT_ID)).thenReturn(Mono.never());

        StepVerifier.create(filter.filter(exchange(GOFP_ROUTE), chain).contextWrite(securityCtx(sub.toString())))
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        verify(chain).filter(any());
        assertThat(registry.find("gofp.freelancer.provisioning.failures").counter().count()).isEqualTo(1.0);
    }

    // ── C-18 test 6 : best-effort ─────────────────────────────────────────

    @Test
    void filter_projectionError_requestSucceeds_andFailureCounterIncremented() {
        UUID sub = UUID.randomUUID();
        when(projectionService.projectIfAbsent(sub, TENANT_ID))
                .thenReturn(Mono.error(new RuntimeException("DB unavailable")));

        StepVerifier.create(filter.filter(exchange(GOFP_ROUTE), chain).contextWrite(securityCtx(sub.toString())))
                .verifyComplete();

        verify(chain).filter(any());
        assertThat(registry.find("gofp.freelancer.provisioning.failures").counter().count()).isEqualTo(1.0);
    }

    @Test
    void filter_validSub_delegatesToProjectionWithSubAndTenant() {
        UUID sub = UUID.randomUUID();
        when(projectionService.projectIfAbsent(sub, TENANT_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(filter.filter(exchange(GOFP_ROUTE), chain).contextWrite(securityCtx(sub.toString())))
                .verifyComplete();

        verify(projectionService).projectIfAbsent(sub, TENANT_ID);
        assertThat(registry.find("gofp.freelancer.provisioning.failures").counter().count()).isZero();
    }

    @Test
    void filter_noSecurityContext_skipsProjection() {
        StepVerifier.create(filter.filter(exchange(GOFP_ROUTE), chain)).verifyComplete();

        verify(projectionService, never()).projectIfAbsent(any(), any());
        verify(chain).filter(any());
    }

    @Test
    void filter_principalNameNotUuid_skipsProjection() {
        StepVerifier.create(filter.filter(exchange(GOFP_ROUTE), chain).contextWrite(securityCtx("platform-client-x")))
                .verifyComplete();

        verify(projectionService, never()).projectIfAbsent(any(), any());
        verify(chain).filter(any());
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }

    private Context securityCtx(String name) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(name);
        return ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth)));
    }
}
