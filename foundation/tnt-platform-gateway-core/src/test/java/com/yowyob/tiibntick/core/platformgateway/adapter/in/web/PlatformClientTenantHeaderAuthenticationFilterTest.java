package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAuditRecorder;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAuthenticationService;
import com.yowyob.tiibntick.core.platformgateway.domain.exception.TntPlatformGatewayException;
import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClientApplication;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Audit n°7 · #4 remediation (2026-07-18) — proves
 * {@link PlatformClientTenantHeaderAuthenticationFilter}'s three-way behaviour:
 * <ol>
 *   <li>no {@code X-Client-Id}/{@code X-Api-Key} at all → pure pass-through, no
 *       authentication set (so a spoofed {@code X-Tenant-Id} header alone, with nothing
 *       else, reaches the controller with NO Authentication in context — the downstream
 *       {@code JwtOrPlatformScopeAuthorizationManager}/{@code oauth2ResourceServer} chain
 *       is what actually rejects it, exercised in
 *       {@link JwtOrPlatformScopeAuthorizationManagerTest#deniesWhenNoAuthenticationPresentAtAll()});</li>
 *   <li>valid Client-Id/Api-Key + valid {@code X-Tenant-Id} → authenticates, attaches a
 *       synthetic {@code TENANT_<uuid>} authority (what makes {@code @CurrentUser
 *       TntUserIdentity} resolve the tenant for this call);</li>
 *   <li>valid Client-Id/Api-Key but missing/malformed {@code X-Tenant-Id} → rejected
 *       (400/401), never silently proceeds with no tenant.</li>
 * </ol>
 *
 * @author MANFOUO Braun
 */
class PlatformClientTenantHeaderAuthenticationFilterTest {

    private final PlatformClientAuthenticationService authenticationService = mock(PlatformClientAuthenticationService.class);
    private final PlatformClientAuditRecorder auditRecorder = mock(PlatformClientAuditRecorder.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PlatformClientTenantHeaderAuthenticationFilter filter =
            new PlatformClientTenantHeaderAuthenticationFilter(authenticationService, auditRecorder, objectMapper);

    @Test
    void passesThroughUntouchedWhenNoPlatformClientHeadersPresent() {
        UUID spoofedTenant = UUID.randomUUID();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/disputes")
                        .header("X-Tenant-Id", spoofedTenant.toString())
                        .build());

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        Mono<Void> result = filter.filter(exchange, ex -> captureAuthentication(capturedAuth));

        result.block();

        assertThat(capturedAuth.get())
                .as("no platform-client credentials were presented — the filter must not authenticate anything itself")
                .isNull();
        verify(authenticationService, never()).authenticate(anyString(), anyString());
    }

    @Test
    void authenticatesAndAttachesTenantAuthorityForValidDisputeCall() {
        UUID tenantId = UUID.randomUUID();
        PlatformClientApplication principal = new PlatformClientApplication(
                UUID.randomUUID(), "agency-back-core", "AGENCY", Environment.PROD, Set.of("DISPUTE:*"));
        when(authenticationService.authenticate("agency-back-core", "tnt_validkey")).thenReturn(Mono.just(principal));
        doNothing().when(auditRecorder).record(any(), anyString(), anyString(), anyString(), any(), any(), any());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/disputes")
                        .header("X-Client-Id", "agency-back-core")
                        .header("X-Api-Key", "tnt_validkey")
                        .header("X-Tenant-Id", tenantId.toString())
                        .build());

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        filter.filter(exchange, ex -> captureAuthentication(capturedAuth)).block();

        assertThat(capturedAuth.get()).isInstanceOf(PlatformClientAuthenticationToken.class);
        assertThat(capturedAuth.get().getAuthorities())
                .extracting(Object::toString)
                .contains("TENANT_" + tenantId);
    }

    @Test
    void attachesSalesReadWriteAuthoritiesOnlyForSalesOrdersPath() {
        UUID tenantId = UUID.randomUUID();
        PlatformClientApplication principal = new PlatformClientApplication(
                UUID.randomUUID(), "agency-back-core", "AGENCY", Environment.PROD, Set.of("SALES:*"));
        when(authenticationService.authenticate("agency-back-core", "tnt_validkey")).thenReturn(Mono.just(principal));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/sales/orders")
                        .header("X-Client-Id", "agency-back-core")
                        .header("X-Api-Key", "tnt_validkey")
                        .header("X-Tenant-Id", tenantId.toString())
                        .build());

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        filter.filter(exchange, ex -> captureAuthentication(capturedAuth)).block();

        assertThat(capturedAuth.get().getAuthorities())
                .extracting(Object::toString)
                .contains("sales:read", "sales:write", "TENANT_" + tenantId);
    }

    @Test
    void rejectsWhenClientIdAndApiKeyValidButTenantHeaderMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/disputes")
                        .header("X-Client-Id", "agency-back-core")
                        .header("X-Api-Key", "tnt_validkey")
                        .build());

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        verify(authenticationService, never()).authenticate(anyString(), anyString());
    }

    @Test
    void rejectsWhenClientIdOrApiKeyInvalid() {
        when(authenticationService.authenticate("agency-back-core", "tnt_wrongkey"))
                .thenReturn(Mono.error(TntPlatformGatewayException.unauthorized("Invalid X-Client-Id / X-Api-Key")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/disputes")
                        .header("X-Client-Id", "agency-back-core")
                        .header("X-Api-Key", "tnt_wrongkey")
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .build());

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    private static Mono<Void> captureAuthentication(AtomicReference<Authentication> sink) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .doOnNext(sink::set)
                .then();
    }
}
