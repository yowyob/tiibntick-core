package com.yowyob.tiibntick.core.product.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.TntCurrentUserArgumentResolver;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.product.application.port.in.ActivateProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.ArchiveProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.CreateProductCommand;
import com.yowyob.tiibntick.core.product.application.port.in.CreateProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.GetProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.ListProductsByTenantUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test proving tenant isolation for {@link ProductController}: this module used to
 * expose a second, parallel route ({@code ProductHandler}/{@code ProductRouterConfig}, a plain
 * WebFlux {@code RouterFunction} at {@code /api/v1/products}/{@code /api/v1/service-offers}) that
 * resolved the tenant straight off the client-supplied {@code X-Tenant-Id} header — a live
 * cross-tenant IDOR, since both that insecure route and this annotated {@code @RestController}
 * (which correctly resolves the tenant from {@code @CurrentUser TntUserIdentity}) were
 * simultaneously reachable in the running app. The insecure handler/router pair has been
 * deleted entirely (no internal caller ever targeted the {@code /api/v1/...} paths — see the
 * repo-wide grep recorded in the fix commit); this test guards the surviving route so any
 * future regression that reintroduces a header-trusting tenant resolution is caught here.
 *
 * <p>Same pattern as {@code SalesOrderControllerTenantHeaderIgnoredTest},
 * {@code DisputeControllerTenantHeaderIgnoredTest}, {@code ReportingControllerTenantHeaderIgnoredTest}.
 *
 * @author MANFOUO Braun
 */
class ProductControllerTenantHeaderIgnoredTest {

    @Test
    void createProduct_ignoresSpoofedTenantHeader_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenantHeader = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenantHeader);

        CreateProductUseCase createProductUseCase = mock(CreateProductUseCase.class);
        GetProductUseCase getProductUseCase = mock(GetProductUseCase.class);
        ListProductsByTenantUseCase listProductsUseCase = mock(ListProductsByTenantUseCase.class);
        ActivateProductUseCase activateProductUseCase = mock(ActivateProductUseCase.class);
        ArchiveProductUseCase archiveProductUseCase = mock(ArchiveProductUseCase.class);

        ProductController controller = new ProductController(
                createProductUseCase, getProductUseCase, listProductsUseCase,
                activateProductUseCase, archiveProductUseCase);

        when(createProductUseCase.createProduct(any())).thenReturn(Mono.error(new RuntimeException("stop-test")));

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

        ProductController.CreateProductRequest request = new ProductController.CreateProductRequest(
                "SKU-1", "Test Product", "desc", null, null, "PHYSICAL_GOOD",
                1000.0, "XAF", "UNIT", 1.0, List.of(), Map.of());

        webClient.post()
                .uri("/api/products")
                // The spoofed header a malicious/careless caller would send — must be ignored.
                .header("X-Tenant-Id", attackerSuppliedTenantHeader.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().is5xxServerError(); // stop-test error surfaces as 500 — irrelevant to what we assert below

        ArgumentCaptor<CreateProductCommand> captor = ArgumentCaptor.forClass(CreateProductCommand.class);
        verify(createProductUseCase).createProduct(captor.capture());
        assertThat(captor.getValue().tenantId())
                .as("tenant must come from the resolved identity, never from the client-supplied X-Tenant-Id header")
                .isEqualTo(resolvedTenant)
                .isNotEqualTo(attackerSuppliedTenantHeader);
    }
}
