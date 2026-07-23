package com.yowyob.tiibntick.core.sales.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.TntCurrentUserArgumentResolver;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.sales.adapter.in.web.dto.request.CreateOrderLineRequest;
import com.yowyob.tiibntick.core.sales.adapter.in.web.dto.request.CreateSalesOrderRequest;
import com.yowyob.tiibntick.core.sales.adapter.in.web.dto.request.DeliveryAddressRequest;
import com.yowyob.tiibntick.core.sales.application.port.in.CreateTntSalesOrderCommand;
import com.yowyob.tiibntick.core.sales.application.service.SalesApplicationService;
import com.yowyob.tiibntick.core.sales.domain.model.OrderPriority;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for Audit n°7 · #4 — {@link SalesOrderController} used to read the
 * tenant straight off the client-supplied {@code X-Tenant-Id} header, so any authenticated
 * caller could create/mutate another tenant's sales orders simply by changing that
 * header. The fix resolves the tenant from {@code @CurrentUser TntUserIdentity} instead
 * (same pattern as {@code ReportingController}, {@code DisputeController}, etc.).
 *
 * <p>This test proves the header is now inert: even when the request carries
 * {@code X-Tenant-Id} for a different ("attacker") tenant, the command passed to
 * {@link SalesApplicationService} still carries the tenant resolved from the
 * authenticated identity, not the header value — this is exactly the shape of the
 * internal call made by {@code coreBackend/tnt-agency-back-core}'s
 * {@code DeliveryMissionClient} once it authenticates via platform Client-Id/Api-Key
 * (see {@code tnt-platform-gateway-core}'s {@code PlatformClientTenantHeaderAuthenticationFilter},
 * which resolves the tenant from the SAME {@code TntUserIdentity} mechanism after
 * validating the caller).
 *
 * @author MANFOUO Braun
 */
class SalesOrderControllerTenantHeaderIgnoredTest {

    @Test
    void createOrder_ignoresSpoofedTenantHeader_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenantHeader = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenantHeader);

        SalesApplicationService service = mock(SalesApplicationService.class);
        SalesOrderController controller = new SalesOrderController(service);

        when(service.createOrder(any())).thenReturn(Mono.error(new RuntimeException("stop-test")));

        ResolveCurrentUserUseCase resolveCurrentUserUseCase = mock(ResolveCurrentUserUseCase.class);
        TntSecurityContext resolvedContext = TntSecurityContext.builder()
                .userId(UUID.randomUUID())
                .tenantId(resolvedTenant)
                .authenticated(true)
                .build();
        when(resolveCurrentUserUseCase.resolveCurrentIdentity())
                .thenReturn(Mono.just(TntUserIdentity.from(resolvedContext)));

        WebTestClient webClient = WebTestClient.bindToController(controller)
                .argumentResolvers(configurer -> configurer.addCustomResolver(
                        new TntCurrentUserArgumentResolver(resolveCurrentUserUseCase)))
                .build();

        CreateSalesOrderRequest request = new CreateSalesOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderLineRequest(UUID.randomUUID(), "Widget", "SKU-1",
                        BigDecimal.ONE, BigDecimal.TEN, "XAF", null)),
                new DeliveryAddressRequest("Rue 1", "Bonapriso", "Douala", "CM", null, null, null, "Jean", "699000000"),
                null,
                OrderPriority.NORMAL,
                "XAF",
                null,
                null);

        webClient.post()
                .uri("/api/sales/orders")
                .header("X-Organization-Id", UUID.randomUUID().toString())
                .header("X-Agency-Id", UUID.randomUUID().toString())
                // The spoofed header a malicious/careless caller would send — must be ignored.
                .header("X-Tenant-Id", attackerSuppliedTenantHeader.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().is5xxServerError(); // stop-test error surfaces as 500 — irrelevant to what we assert below

        ArgumentCaptor<CreateTntSalesOrderCommand> captor = ArgumentCaptor.forClass(CreateTntSalesOrderCommand.class);
        verify(service).createOrder(captor.capture());
        assertThat(captor.getValue().tenantId())
                .as("tenant must come from the resolved identity, never from the client-supplied X-Tenant-Id header")
                .isEqualTo(resolvedTenant)
                .isNotEqualTo(attackerSuppliedTenantHeader);
    }
}
