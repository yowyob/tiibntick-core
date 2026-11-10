package com.yowyob.tiibntick.core.billing.report.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.report.adapter.in.web.mapper.ReportWebMapper;
import com.yowyob.tiibntick.core.billing.report.application.port.out.BillingKPISnapshotRepository;
import com.yowyob.tiibntick.core.billing.report.application.port.out.InvoiceReportProjectionRepository;
import com.yowyob.tiibntick.core.billing.report.application.port.out.ReportExportPort;
import com.yowyob.tiibntick.core.billing.report.application.service.ReportingService;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Per-issuer reports: the real controller, the real {@link ReportingService} behind the real permission aspect
 * (so {@code report:read} / {@code report:own} are genuinely enforced) and a mocked projection repository.
 * Only the transport and the JWT-derived identity are faked.
 */
class ReportAccessControlTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DRIVER = UUID.randomUUID();
    private static final UUID OTHER_DRIVER = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    private InvoiceReportProjectionRepository projection;
    private ReportingService proxied;
    private TntPermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        projection = mock(InvoiceReportProjectionRepository.class);
        when(projection.findByFreelancerOrg(any(), any(), any())).thenReturn(Flux.empty());
        when(projection.sumTotalSurchargeByOwnerOrgAndPeriod(any(), any(), any(), any()))
                .thenReturn(Mono.just(BigDecimal.TEN));
        when(projection.findByAppliedTemplateName(any(), any(), any())).thenReturn(Flux.empty());

        ReportingService service = new ReportingService(projection, mock(BillingKPISnapshotRepository.class),
                mock(ReportExportPort.class));
        evaluator = new TntPermissionEvaluator(mock(ReactivePermissionResolver.class), new TntRoleDefinitionRegistry());
        AspectJProxyFactory factory = new AspectJProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAspect(new TntPermissionAspect(evaluator));
        proxied = factory.getProxy();
    }

    private WebTestClient as(UUID userId, UUID actorId, String... authorities) {
        var auth = new TestingAuthenticationToken("u", "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        auth.setAuthenticated(true);
        ReportingController controller = new ReportingController(proxied, mock(ReportWebMapper.class),
                new ReportAccessPolicy(evaluator));
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new TntRoleExceptionHandler())
                .argumentResolvers(r -> r.addCustomResolver(new IdentityResolver(userId, actorId)))
                .webFilter((ex, chain) -> chain.filter(ex)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .build();
    }

    private static String freelancerOrg(UUID id) {
        return "/api/v1/billing/reports/freelancer-org/" + id + "?from=2026-01-01&to=2026-01-31";
    }

    private static String surcharge(UUID id) {
        return "/api/v1/billing/reports/surcharge-analytics?ownerOrgId=" + id
                + "&ownerOrgType=FREELANCER_ORG&from=2026-01-01&to=2026-01-31";
    }

    @Test
    @DisplayName("a driver (report:own) reads their own report, scoped to the tenant of their token")
    void driverReadsOwnReport() {
        as(DRIVER, ACTOR, "report:own").get().uri(freelancerOrg(DRIVER)).exchange().expectStatus().isOk();

        verify(projection).findByFreelancerOrg(eq(TENANT), any(), eq(DRIVER.toString()));
    }

    @Test
    @DisplayName("a driver may also use their actor id as issuer id")
    void driverReadsOwnReportByActorId() {
        as(DRIVER, ACTOR, "report:own").get().uri(freelancerOrg(ACTOR)).exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("a driver is refused another driver's report, nothing is queried")
    void driverCannotReadSomeoneElse() {
        as(DRIVER, ACTOR, "report:own").get().uri(freelancerOrg(OTHER_DRIVER)).exchange()
                .expectStatus().isForbidden();

        verify(projection, never()).findByFreelancerOrg(any(), any(), any());
    }

    @Test
    @DisplayName("a driver is refused another issuer's surcharge analytics")
    void driverCannotReadOtherSurcharge() {
        as(DRIVER, ACTOR, "report:own").get().uri(surcharge(OTHER_DRIVER)).exchange()
                .expectStatus().isForbidden();
        as(DRIVER, ACTOR, "report:own").get().uri(surcharge(DRIVER)).exchange().expectStatus().isOk();

        verify(projection).sumTotalSurchargeByOwnerOrgAndPeriod(eq(TENANT), any(), eq(DRIVER.toString()), any());
    }

    @Test
    @DisplayName("management (report:read) reads any issuer of their tenant")
    void staffReadsAnyIssuer() {
        as(UUID.randomUUID(), null, "report:read").get().uri(freelancerOrg(OTHER_DRIVER)).exchange()
                .expectStatus().isOk();

        verify(projection).findByFreelancerOrg(eq(TENANT), any(), eq(OTHER_DRIVER.toString()));
    }

    @Test
    @DisplayName("the tenant is the token's: a tenantId request parameter is ignored")
    void tenantParamIgnored() {
        UUID attacker = UUID.randomUUID();
        as(UUID.randomUUID(), null, "report:read").get()
                .uri(freelancerOrg(OTHER_DRIVER) + "&tenantId=" + attacker).exchange().expectStatus().isOk();

        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        verify(projection).findByFreelancerOrg(tenant.capture(), any(), any());
        assertThat(tenant.getValue()).isEqualTo(TENANT).isNotEqualTo(attacker);
    }

    @Test
    @DisplayName("template usage is management-only")
    void templateUsageStaffOnly() {
        String uri = "/api/v1/billing/reports/template-usage?from=2026-01-01&to=2026-01-31";
        as(DRIVER, ACTOR, "report:own").get().uri(uri).exchange().expectStatus().isForbidden();
        as(UUID.randomUUID(), null, "report:read").get().uri(uri).exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("a caller with no report permission at all is refused everywhere")
    void noPermissionNoReport() {
        as(DRIVER, ACTOR, "wallet:read").get().uri(freelancerOrg(DRIVER)).exchange().expectStatus().isForbidden();
        as(DRIVER, ACTOR, "wallet:read").get().uri(surcharge(DRIVER)).exchange().expectStatus().isForbidden();
        verify(projection, never()).findByFreelancerOrg(any(), any(), any());
    }

    private record IdentityResolver(UUID userId, UUID actorId) implements HandlerMethodArgumentResolver {
        @Override public boolean supportsParameter(MethodParameter p) {
            return p.hasParameterAnnotation(CurrentUser.class);
        }

        @Override public Mono<Object> resolveArgument(MethodParameter p, BindingContext c, ServerWebExchange e) {
            return Mono.just(new TntUserIdentity(userId, TENANT, actorId, null, null, Set.of(), false));
        }
    }
}
