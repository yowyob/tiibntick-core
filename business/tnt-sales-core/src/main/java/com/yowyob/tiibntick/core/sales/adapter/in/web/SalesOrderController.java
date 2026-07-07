package com.yowyob.tiibntick.core.sales.adapter.in.web;

import com.yowyob.tiibntick.core.sales.adapter.in.web.dto.request.*;
import com.yowyob.tiibntick.core.sales.adapter.in.web.dto.response.SalesOrderResponse;
import com.yowyob.tiibntick.core.sales.application.port.in.*;
import com.yowyob.tiibntick.core.sales.application.service.SalesApplicationService;
import com.yowyob.tiibntick.core.sales.domain.model.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for TiiBnTick sales order lifecycle management.
 * Base path: /api/sales/orders
 * Author: MANFOUO Braun
 */
@RestController
@RequestMapping("/api/sales/orders")
public class SalesOrderController {

    private final SalesApplicationService service;

    public SalesOrderController(SalesApplicationService service) {
        this.service = service;
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> createOrder(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-Organization-Id") UUID organizationId,
            @RequestHeader("X-Agency-Id") UUID agencyId,
            @Valid @RequestBody CreateSalesOrderRequest req) {

        var lines = req.lines().stream()
                .map(l -> new CreateTntOrderLineCommand(l.productId(), l.productName(), l.sku(),
                        l.quantity(), l.unitPrice(), l.currency(), l.notes()))
                .toList();

        TntAddress delivery = toAddress(req.deliveryAddress());
        TntAddress billing  = req.billingAddress() != null ? toAddress(req.billingAddress()) : null;

        var cmd = new CreateTntSalesOrderCommand(tenantId, organizationId, agencyId,
                req.clientThirdPartyId(), lines, delivery, billing, req.priority(), req.currency(), null,
                req.providerOrgType(), req.providerOrgId()); //  FreelancerOrg provider

        return service.createOrder(cmd)
                .map(o -> ResponseEntity.status(HttpStatus.CREATED).body(SalesOrderResponse.from(o)));
    }

    // ─── Get / List ───────────────────────────────────────────────────────────

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('sales:read')")
    public Mono<ResponseEntity<SalesOrderResponse>> getOrder(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID orderId) {
        return service.getOrder(tenantId, orderId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales:read')")
    public Mono<ResponseEntity<List<SalesOrderResponse>>> listByStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) SalesOrderStatus status) {
        var flux = status != null
                ? service.listByStatus(tenantId, status)
                : service.listPendingDispatch(tenantId);
        return flux.map(SalesOrderResponse::from).collectList().map(ResponseEntity::ok);
    }

    @GetMapping("/by-client/{clientThirdPartyId}")
    @PreAuthorize("hasAuthority('sales:read')")
    public Mono<ResponseEntity<List<SalesOrderResponse>>> listByClient(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID clientThirdPartyId) {
        return service.listByClient(tenantId, clientThirdPartyId)
                .map(SalesOrderResponse::from).collectList().map(ResponseEntity::ok);
    }

    @GetMapping("/by-agency/{agencyId}")
    @PreAuthorize("hasAuthority('sales:read')")
    public Mono<ResponseEntity<List<SalesOrderResponse>>> listByAgency(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestParam(required = false) String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        return service.listByAgency(tenantId, agencyId, ym)
                .map(SalesOrderResponse::from).collectList().map(ResponseEntity::ok);
    }

    // ─── Lifecycle transitions ────────────────────────────────────────────────

    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> confirm(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID orderId) {
        return service.confirmOrder(tenantId, orderId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/reserve-stock")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> reserveStock(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID orderId) {
        return service.reserveStock(tenantId, orderId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/dispatch")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> dispatch(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody DispatchOrderRequest req) {
        return service.dispatch(tenantId, orderId, req.missionId())
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/start-delivery")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> startDelivery(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID orderId) {
        return service.startDelivery(tenantId, orderId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/deliver")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> deliver(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID orderId) {
        return service.markDelivered(tenantId, orderId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/return")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> returnOrder(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody ReturnOrderRequest req) {
        return service.returnOrder(tenantId, orderId, req.reason(), req.note())
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) CancelOrderRequest req) {
        String reason = req != null ? req.reason() : null;
        return service.cancelOrder(tenantId, orderId, reason)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PatchMapping("/{orderId}/link-invoice/{invoiceId}")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> linkInvoice(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID orderId,
            @PathVariable UUID invoiceId) {
        return service.linkInvoice(tenantId, orderId, invoiceId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private TntAddress toAddress(DeliveryAddressRequest r) {
        return new TntAddress(r.street(), r.quartier(), r.city(), r.country(),
                r.landmark(), r.latitude(), r.longitude(), r.recipientName(), r.recipientPhone());
    }
}
