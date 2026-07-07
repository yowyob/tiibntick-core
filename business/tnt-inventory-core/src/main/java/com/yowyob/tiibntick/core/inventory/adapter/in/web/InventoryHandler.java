package com.yowyob.tiibntick.core.inventory.adapter.in.web;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
public class InventoryHandler {

    private final ReceiveStockUseCase receiveStockUseCase;
    private final ReserveStockUseCase reserveStockUseCase;
    private final GetStockEntryUseCase getStockEntryUseCase;
    private final GetLowStockAlertsUseCase getLowStockAlertsUseCase;
    private final DepositHubPackageUseCase depositHubPackageUseCase;
    private final PickupHubPackageUseCase pickupHubPackageUseCase;
    private final GetHubOccupancyUseCase getHubOccupancyUseCase;
    private final FindOverduePackagesUseCase findOverduePackagesUseCase;

    public InventoryHandler(ReceiveStockUseCase receiveStockUseCase,
                            ReserveStockUseCase reserveStockUseCase,
                            GetStockEntryUseCase getStockEntryUseCase,
                            GetLowStockAlertsUseCase getLowStockAlertsUseCase,
                            DepositHubPackageUseCase depositHubPackageUseCase,
                            PickupHubPackageUseCase pickupHubPackageUseCase,
                            GetHubOccupancyUseCase getHubOccupancyUseCase,
                            FindOverduePackagesUseCase findOverduePackagesUseCase) {
        this.receiveStockUseCase = receiveStockUseCase;
        this.reserveStockUseCase = reserveStockUseCase;
        this.getStockEntryUseCase = getStockEntryUseCase;
        this.getLowStockAlertsUseCase = getLowStockAlertsUseCase;
        this.depositHubPackageUseCase = depositHubPackageUseCase;
        this.pickupHubPackageUseCase = pickupHubPackageUseCase;
        this.getHubOccupancyUseCase = getHubOccupancyUseCase;
        this.findOverduePackagesUseCase = findOverduePackagesUseCase;
    }

    public Mono<ServerResponse> receiveStock(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        return request.bodyToMono(StockOperationRequest.class)
                .flatMap(body -> receiveStockUseCase.receiveStock(new ReceiveStockCommand(
                        tenantId, UUID.fromString(body.productId()), UUID.fromString(body.warehouseId()),
                        body.quantity(), body.reference(), body.notes(),
                        body.performedBy() != null ? UUID.fromString(body.performedBy()) : null)))
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> reserveStock(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        return request.bodyToMono(StockOperationRequest.class)
                .flatMap(body -> reserveStockUseCase.reserveStock(new ReserveStockCommand(
                        tenantId, UUID.fromString(body.productId()),
                        UUID.fromString(body.warehouseId()), body.quantity(), body.reference())))
                .then(ServerResponse.noContent().build())
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage())));
    }

    public Mono<ServerResponse> getStockEntry(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        UUID productId = UUID.fromString(request.queryParam("productId").orElseThrow());
        UUID warehouseId = UUID.fromString(request.queryParam("warehouseId").orElseThrow());
        return getStockEntryUseCase.getStockEntry(tenantId, productId, warehouseId)
                .flatMap(entry -> ServerResponse.ok().bodyValue(entry))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> getLowStockAlerts(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        return getLowStockAlertsUseCase.getLowStockAlerts(tenantId)
                .collectList()
                .flatMap(alerts -> ServerResponse.ok().bodyValue(alerts));
    }

    public Mono<ServerResponse> depositHubPackage(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        UUID hubId = UUID.fromString(request.pathVariable("hubId"));
        return request.bodyToMono(DepositPackageRequest.class)
                .flatMap(body -> depositHubPackageUseCase.depositPackage(new DepositHubPackageCommand(
                        tenantId, hubId, UUID.fromString(body.packageId()), body.trackingCode(),
                        body.storageLocation(),
                        body.depositedByActorId() != null ? UUID.fromString(body.depositedByActorId()) : null,
                        body.recipientPhone())))
                .flatMap(entry -> ServerResponse.status(HttpStatus.CREATED).bodyValue(entry));
    }

    public Mono<ServerResponse> pickupHubPackage(ServerRequest request) {
        String trackingCode = request.pathVariable("trackingCode");
        return request.bodyToMono(PickupRequest.class)
                .flatMap(body -> pickupHubPackageUseCase.pickupPackage(trackingCode,
                        UUID.fromString(body.pickedUpByActorId())))
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> getHubOccupancy(ServerRequest request) {
        UUID hubId = UUID.fromString(request.pathVariable("hubId"));
        int maxCapacity = Integer.parseInt(request.queryParam("maxCapacity").orElse("100"));
        return getHubOccupancyUseCase.getOccupancyRate(hubId, maxCapacity)
                .flatMap(rate -> ServerResponse.ok().bodyValue(Map.of("occupancyRate", rate)));
    }

    public Mono<ServerResponse> findOverduePackages(ServerRequest request) {
        UUID hubId = UUID.fromString(request.pathVariable("hubId"));
        long maxHours = Long.parseLong(request.queryParam("maxHours").orElse("48"));
        return findOverduePackagesUseCase.findOverduePackages(hubId, maxHours)
                .collectList()
                .flatMap(packages -> ServerResponse.ok().bodyValue(packages));
    }

    record StockOperationRequest(
            String productId, String warehouseId, double quantity,
            String reference, String notes, String performedBy
    ) {}

    record DepositPackageRequest(
            String packageId, String trackingCode, String storageLocation,
            String depositedByActorId, String recipientPhone
    ) {}

    record PickupRequest(String pickedUpByActorId) {}
}
