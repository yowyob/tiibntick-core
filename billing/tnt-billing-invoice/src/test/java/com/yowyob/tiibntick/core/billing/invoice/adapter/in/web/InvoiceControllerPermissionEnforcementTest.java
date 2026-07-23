package com.yowyob.tiibntick.core.billing.invoice.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.mapper.InvoiceWebMapper;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.CreditNoteRepository;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoiceEventPublisher;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoicePdfPort;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoiceRepository;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoiceSequencePort;
import com.yowyob.tiibntick.core.billing.invoice.application.service.InvoiceService;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for Audit n°7 · #6 — proves that {@code @RequirePermission} on
 * {@link InvoiceService} mutation methods is not just present in source but actually
 * enforced end-to-end at runtime, and that a denied call surfaces as HTTP 403 (not the
 * 500 it silently produced before {@code TntRoleExceptionHandler} existed — see that
 * class's javadoc for why nothing in the repo previously mapped {@code TntRoleException}
 * to an HTTP status).
 *
 * <p>Wires the real {@link TntPermissionAspect} + {@link TntPermissionEvaluator} around a
 * real {@link InvoiceService} instance via {@link AspectJProxyFactory} (deliberately
 * lighter than a {@code @SpringBootTest} — no Spring context needed), then drives an HTTP
 * call through {@link InvoiceController#cancel} with a Spring Security {@code Authentication}
 * that carries no {@code invoice:cancel} authority. The call must be rejected with 403
 * before the (mocked) repository is ever touched.
 *
 * @author MANFOUO Braun
 */
class InvoiceControllerPermissionEnforcementTest {

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    @DisplayName("cancel is rejected with 403 for an authenticated caller lacking invoice:cancel")
    void cancel_withoutPermission_returns403() {
        InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);

        // Authenticated, but the JWT-derived authorities grant no invoice permission at all
        // (e.g. a CLIENT-role user hitting an AGENCY_MANAGER-only operation).
        var noPermissionAuth = new TestingAuthenticationToken("user", "n/a", List.of());
        noPermissionAuth.setAuthenticated(true);

        WebTestClient client = buildClient(invoiceRepository, noPermissionAuth);

        client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/billing/invoices/{invoiceId}/cancel")
                        .queryParam("reason", "customer request")
                        .build(INVOICE_ID))
                .exchange()
                .expectStatus().isForbidden();

        verify(invoiceRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    @DisplayName("cancel is NOT blocked by the AOP guard once invoice:cancel is granted")
    void cancel_withPermission_isNotBlockedByAop() {
        InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
        // No invoice exists -> InvoiceService throws InvoiceNotFoundException AFTER the
        // permission check passes. Getting a 404 (not 403) here proves the AOP guard let
        // the call through once the right authority is present.
        when(invoiceRepository.findByIdAndTenantId(any(), any())).thenReturn(Mono.empty());

        var withPermissionAuth = new TestingAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("invoice:cancel")));
        withPermissionAuth.setAuthenticated(true);

        WebTestClient client = buildClient(invoiceRepository, withPermissionAuth);

        client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/billing/invoices/{invoiceId}/cancel")
                        .queryParam("reason", "customer request")
                        .build(INVOICE_ID))
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Builds a WebTestClient wired with a real (non-mocked) InvoiceService proxied by the
     * real {@link TntPermissionAspect}, and a reactive security context carrying the given
     * {@code authentication} for every request — so {@code @RequirePermission} is genuinely
     * enforced against real Spring Security authorities, not a stub.
     */
    private WebTestClient buildClient(InvoiceRepository invoiceRepository,
                                       org.springframework.security.core.Authentication authentication) {
        InvoiceService realService = new InvoiceService(
                invoiceRepository,
                mock(CreditNoteRepository.class),
                mock(InvoiceEventPublisher.class),
                mock(InvoicePdfPort.class),
                mock(InvoiceSequencePort.class));

        TntRoleDefinitionRegistry registry = new TntRoleDefinitionRegistry();
        TntPermissionEvaluator evaluator = new TntPermissionEvaluator(mock(ReactivePermissionResolver.class), registry);
        TntPermissionAspect aspect = new TntPermissionAspect(evaluator);

        AspectJProxyFactory factory = new AspectJProxyFactory(realService);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        InvoiceService proxiedService = factory.getProxy();

        InvoiceController controller = new InvoiceController(proxiedService, new InvoiceWebMapper());

        return WebTestClient.bindToController(controller)
                .controllerAdvice(new TntRoleExceptionHandler(), new InvoiceExceptionHandler())
                .argumentResolvers(resolvers -> resolvers.addCustomResolver(new FixedCurrentUserArgumentResolver()))
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .build();
    }

    /** Always resolves {@code @CurrentUser} to a fixed identity — tenant is irrelevant to this test. */
    static class FixedCurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
                                             ServerWebExchange exchange) {
            return Mono.just(new TntUserIdentity(
                    UUID.randomUUID(), TENANT_ID, UUID.randomUUID(), null, null, Set.of(), false));
        }
    }
}
