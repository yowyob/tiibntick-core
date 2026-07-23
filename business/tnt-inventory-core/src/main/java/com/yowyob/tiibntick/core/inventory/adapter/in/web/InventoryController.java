package com.yowyob.tiibntick.core.inventory.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.inventory.application.port.in.*;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import com.yowyob.tiibntick.core.inventory.domain.model.InventoryAlert;
import com.yowyob.tiibntick.core.inventory.domain.model.StockEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for TiiBnTick inventory stock and relay-hub package management.
 *
 * <p><b>Security fix (Audit n7):</b> replaces the former {@code InventoryHandler}
 * (a plain {@code @Component}) + {@code InventoryRouterConfig} (a WebFlux
 * {@code RouterFunction} bean) pair, which resolved the multi-tenancy key by trusting
 * whatever {@code X-Tenant-Id} header the HTTP caller supplied
 * ({@code UUID.fromString(request.headers().firstHeader("X-Tenant-Id"))}) — a live
 * cross-tenant IDOR on every mutation endpoint this module exposed. Every endpoint here
 * instead resolves the tenant from {@code @CurrentUser TntUserIdentity}, populated from
 * the caller's authenticated JWT (or, for platform-backend callers, the identity resolved
 * by the platform Client-Id/Api-Key gateway) — never from a client-supplied header. Same
 * pattern as {@code ProductController}/{@code EquipmentController}
 * ({@code tnt-product-core}/{@code tnt-resource-core}).
 *
 * <p><b>Base path:</b> {@code /api/inventory}, following this codebase's newer
 * {@code /api/<domain>} convention (see {@code /api/products}, {@code /api/sales/orders},
 * {@code /api/resources/equipment}, {@code /api/accounting/accounts}) rather than the
 * retired {@code /api/v1/...} prefix the old functional router used. Route suffixes are
 * kept identical to the old router (e.g. {@code /stock/receive}, {@code /hubs/{hubId}/packages})
 * so only the host application's proxy/gateway path-rewrite (if any) and the one internal
 * caller in this repo — {@code coreBackend/tnt-agency-back-core}'s {@code InventoryCoreClient}
 * (updated alongside this controller) — needed to change.
 *
 * <p>Two hub endpoints ({@code pickupHubPackage}) plus the two hub read endpoints
 * ({@code getHubOccupancy}, {@code findOverduePackages}) never accepted a tenantId at all
 * (neither in the old handler nor in the underlying use-case ports) — that is a
 * pre-existing, separate gap (missing tenant scoping on hub package lookups), not the
 * client-supplied-tenant-header IDOR this controller fixes, and is left untouched here.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Inventory", description = "Stock entries, movements, low-stock alerts, and relay-hub package tracking")
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final ReceiveStockUseCase receiveStockUseCase;
    private final ReserveStockUseCase reserveStockUseCase;
    private final GetStockEntryUseCase getStockEntryUseCase;
    private final GetLowStockAlertsUseCase getLowStockAlertsUseCase;
    private final DepositHubPackageUseCase depositHubPackageUseCase;
    private final PickupHubPackageUseCase pickupHubPackageUseCase;
    private final GetHubOccupancyUseCase getHubOccupancyUseCase;
    private final FindOverduePackagesUseCase findOverduePackagesUseCase;

    @Operation(summary = "Receive stock into a warehouse")
    @PostMapping("/stock/receive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public Mono<Void> receiveStock(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestBody StockOperationRequest body) {
        return receiveStockUseCase.receiveStock(new ReceiveStockCommand(
                currentUser.tenantId(), UUID.fromString(body.productId()), UUID.fromString(body.warehouseId()),
                body.quantity(), body.reference(), body.notes(),
                body.performedBy() != null ? UUID.fromString(body.performedBy()) : null));
    }

    @Operation(summary = "Reserve stock for a pending dispatch")
    @PostMapping("/stock/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public Mono<Void> reserveStock(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestBody StockOperationRequest body) {
        return reserveStockUseCase.reserveStock(new ReserveStockCommand(
                currentUser.tenantId(), UUID.fromString(body.productId()),
                UUID.fromString(body.warehouseId()), body.quantity(), body.reference()));
    }

    @Operation(summary = "Get the stock entry for a product/warehouse pair")
    @GetMapping("/stock")
    public Mono<StockEntry> getStockEntry(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam UUID productId,
            @RequestParam UUID warehouseId) {
        return getStockEntryUseCase.getStockEntry(currentUser.tenantId(), productId, warehouseId);
    }

    @Operation(summary = "List unacknowledged low-stock alerts for the caller's tenant")
    @GetMapping("/alerts/low-stock")
    public Flux<InventoryAlert> getLowStockAlerts(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return getLowStockAlertsUseCase.getLowStockAlerts(currentUser.tenantId());
    }

    @Operation(summary = "Deposit a package at a relay hub")
    @PostMapping("/hubs/{hubId}/packages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public Mono<HubPackageEntry> depositHubPackage(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID hubId,
            @RequestBody DepositPackageRequest body) {
        return depositHubPackageUseCase.depositPackage(new DepositHubPackageCommand(
                currentUser.tenantId(), hubId, UUID.fromString(body.packageId()), body.trackingCode(),
                body.storageLocation(),
                body.depositedByActorId() != null ? UUID.fromString(body.depositedByActorId()) : null,
                body.recipientPhone()));
    }

    @Operation(summary = "Confirm pickup of a hub package by tracking code")
    @PostMapping("/hub-packages/{trackingCode}/pickup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public Mono<Void> pickupHubPackage(
            @PathVariable String trackingCode,
            @RequestBody PickupRequest body) {
        return pickupHubPackageUseCase.pickupPackage(trackingCode, UUID.fromString(body.pickedUpByActorId()));
    }

    @Operation(summary = "Get the occupancy rate of a relay hub")
    @GetMapping("/hubs/{hubId}/occupancy")
    public Mono<Map<String, Double>> getHubOccupancy(
            @PathVariable UUID hubId,
            @RequestParam(defaultValue = "100") int maxCapacity) {
        return getHubOccupancyUseCase.getOccupancyRate(hubId, maxCapacity)
                .map(rate -> Map.of("occupancyRate", rate));
    }

    @Operation(summary = "Find packages overdue for pickup at a relay hub")
    @GetMapping("/hubs/{hubId}/overdue")
    public Flux<HubPackageEntry> findOverduePackages(
            @PathVariable UUID hubId,
            @RequestParam(defaultValue = "48") long maxHours) {
        return findOverduePackagesUseCase.findOverduePackages(hubId, maxHours);
    }

    // ── Request DTOs ─────────────────────────────────────────────────────────

    public record StockOperationRequest(
            String productId, String warehouseId, double quantity,
            String reference, String notes, String performedBy
    ) {}

    public record DepositPackageRequest(
            String packageId, String trackingCode, String storageLocation,
            String depositedByActorId, String recipientPhone
    ) {}

    public record PickupRequest(String pickedUpByActorId) {}
}
