package com.yowyob.tiibntick.core.inventory.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.TntCurrentUserArgumentResolver;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageCommand;
import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.FindOverduePackagesUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.GetHubOccupancyUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.GetLowStockAlertsUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.GetStockEntryUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.PickupHubPackageUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.ReceiveStockCommand;
import com.yowyob.tiibntick.core.inventory.application.port.in.ReceiveStockUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.ReserveStockCommand;
import com.yowyob.tiibntick.core.inventory.application.port.in.ReserveStockUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for the Audit n7 cross-tenant IDOR fix — {@code InventoryController}
 * (which replaced the insecure {@code InventoryHandler}/{@code InventoryRouterConfig}
 * functional router) used to read the tenant straight off the client-supplied
 * {@code X-Tenant-Id} header, so any caller reaching the endpoint could mutate another
 * tenant's stock simply by changing that header. The fix resolves the tenant from
 * {@code @CurrentUser TntUserIdentity} instead (same pattern as {@code SalesOrderController},
 * {@code ReportingController}, {@code DisputeController}, etc.).
 *
 * <p>These tests prove the header is now inert: even when the request carries
 * {@code X-Tenant-Id} for a different ("attacker") tenant, the command passed to the
 * underlying use case still carries the tenant resolved from the authenticated identity,
 * never the header value.
 *
 * @author MANFOUO Braun
 */
class InventoryControllerTenantHeaderIgnoredTest {

    @Test
    void receiveStock_ignoresSpoofedTenantHeader_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenantHeader = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenantHeader);

        ReceiveStockUseCase receiveStockUseCase = mock(ReceiveStockUseCase.class);
        when(receiveStockUseCase.receiveStock(any())).thenReturn(Mono.empty());

        InventoryController controller = new InventoryController(
                receiveStockUseCase,
                mock(ReserveStockUseCase.class),
                mock(GetStockEntryUseCase.class),
                mock(GetLowStockAlertsUseCase.class),
                mock(DepositHubPackageUseCase.class),
                mock(PickupHubPackageUseCase.class),
                mock(GetHubOccupancyUseCase.class),
                mock(FindOverduePackagesUseCase.class));

        WebTestClient webClient = webClientFor(controller, resolvedTenant);

        InventoryController.StockOperationRequest request = new InventoryController.StockOperationRequest(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 10.0,
                "REF-1", "notes", null);

        webClient.post()
                .uri("/api/inventory/stock/receive")
                // The spoofed header a malicious/careless caller would send — must be ignored.
                .header("X-Tenant-Id", attackerSuppliedTenantHeader.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent();

        ArgumentCaptor<ReceiveStockCommand> captor = ArgumentCaptor.forClass(ReceiveStockCommand.class);
        verify(receiveStockUseCase).receiveStock(captor.capture());
        assertThat(captor.getValue().tenantId())
                .as("tenant must come from the resolved identity, never from the client-supplied X-Tenant-Id header")
                .isEqualTo(resolvedTenant)
                .isNotEqualTo(attackerSuppliedTenantHeader);
    }

    @Test
    void reserveStock_ignoresSpoofedTenantHeader_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenantHeader = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenantHeader);

        ReserveStockUseCase reserveStockUseCase = mock(ReserveStockUseCase.class);
        when(reserveStockUseCase.reserveStock(any())).thenReturn(Mono.empty());

        InventoryController controller = new InventoryController(
                mock(ReceiveStockUseCase.class),
                reserveStockUseCase,
                mock(GetStockEntryUseCase.class),
                mock(GetLowStockAlertsUseCase.class),
                mock(DepositHubPackageUseCase.class),
                mock(PickupHubPackageUseCase.class),
                mock(GetHubOccupancyUseCase.class),
                mock(FindOverduePackagesUseCase.class));

        WebTestClient webClient = webClientFor(controller, resolvedTenant);

        InventoryController.StockOperationRequest request = new InventoryController.StockOperationRequest(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 5.0,
                "REF-2", null, null);

        webClient.post()
                .uri("/api/inventory/stock/reserve")
                .header("X-Tenant-Id", attackerSuppliedTenantHeader.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent();

        ArgumentCaptor<ReserveStockCommand> captor = ArgumentCaptor.forClass(ReserveStockCommand.class);
        verify(reserveStockUseCase).reserveStock(captor.capture());
        assertThat(captor.getValue().tenantId())
                .as("tenant must come from the resolved identity, never from the client-supplied X-Tenant-Id header")
                .isEqualTo(resolvedTenant)
                .isNotEqualTo(attackerSuppliedTenantHeader);
    }

    @Test
    void depositHubPackage_ignoresSpoofedTenantHeader_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenantHeader = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenantHeader);
        UUID hubId = UUID.randomUUID();

        DepositHubPackageUseCase depositHubPackageUseCase = mock(DepositHubPackageUseCase.class);
        when(depositHubPackageUseCase.depositPackage(any())).thenReturn(Mono.error(new RuntimeException("stop-test")));

        InventoryController controller = new InventoryController(
                mock(ReceiveStockUseCase.class),
                mock(ReserveStockUseCase.class),
                mock(GetStockEntryUseCase.class),
                mock(GetLowStockAlertsUseCase.class),
                depositHubPackageUseCase,
                mock(PickupHubPackageUseCase.class),
                mock(GetHubOccupancyUseCase.class),
                mock(FindOverduePackagesUseCase.class));

        WebTestClient webClient = webClientFor(controller, resolvedTenant);

        InventoryController.DepositPackageRequest request = new InventoryController.DepositPackageRequest(
                UUID.randomUUID().toString(), "TRK-1", "SHELF-A", null, "699000000");

        webClient.post()
                .uri("/api/inventory/hubs/{hubId}/packages", hubId)
                .header("X-Tenant-Id", attackerSuppliedTenantHeader.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().is5xxServerError(); // stop-test error surfaces as 500 — irrelevant to what we assert below

        ArgumentCaptor<DepositHubPackageCommand> captor = ArgumentCaptor.forClass(DepositHubPackageCommand.class);
        verify(depositHubPackageUseCase).depositPackage(captor.capture());
        assertThat(captor.getValue().tenantId())
                .as("tenant must come from the resolved identity, never from the client-supplied X-Tenant-Id header")
                .isEqualTo(resolvedTenant)
                .isNotEqualTo(attackerSuppliedTenantHeader);
    }

    private static WebTestClient webClientFor(InventoryController controller, UUID resolvedTenant) {
        ResolveCurrentUserUseCase resolveCurrentUserUseCase = mock(ResolveCurrentUserUseCase.class);
        TntSecurityContext resolvedContext = TntSecurityContext.builder()
                .userId(UUID.randomUUID())
                .tenantId(resolvedTenant)
                .authenticated(true)
                .build();
        when(resolveCurrentUserUseCase.resolveCurrentIdentity())
                .thenReturn(Mono.just(TntUserIdentity.from(resolvedContext)));

        return WebTestClient.bindToController(controller)
                .argumentResolvers(configurer -> configurer.addCustomResolver(
                        new TntCurrentUserArgumentResolver(resolveCurrentUserUseCase)))
                .build();
    }
}
