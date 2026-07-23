package com.yowyob.tiibntick.core.sales.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.sales.adapter.in.web.dto.request.*;
import com.yowyob.tiibntick.core.sales.adapter.in.web.dto.response.SalesOrderResponse;
import com.yowyob.tiibntick.core.sales.application.port.in.*;
import com.yowyob.tiibntick.core.sales.application.service.SalesApplicationService;
import com.yowyob.tiibntick.core.sales.domain.model.*;
import io.swagger.v3.oas.annotations.Parameter;
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
 *
 * <p>Called both by end users (JWT) and server-to-server by
 * {@code coreBackend/tnt-agency-back-core}'s {@code DeliveryMissionClient} (Kafka-driven
 * mission-creation flows with no end-user session to forward a JWT from). Tenant
 * isolation is enforced via {@code @CurrentUser TntUserIdentity} (Audit n°7 · #4
 * remediation, 2026-07-18) — never the client-supplied {@code X-Tenant-Id} header, which
 * any caller could previously forge to read/write another tenant's orders. The identity
 * resolves from either the end-user JWT or, for the internal caller, a platform
 * Client-Id/Api-Key call scoped to {@code SALES:*} (see {@code tnt-platform-gateway-core}'s
 * {@code TntPlatformGatewaySecurityConfig} {@code @Order(11)} chain) — either way
 * {@code TntUserIdentity} carries the correctly-resolved tenant, so this controller needs
 * no branching logic of its own.
 *
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
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader("X-Organization-Id") UUID organizationId,
            @RequestHeader("X-Agency-Id") UUID agencyId,
            @Valid @RequestBody CreateSalesOrderRequest req) {
        UUID tenantId = currentUser.tenantId();

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
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID orderId) {
        UUID tenantId = currentUser.tenantId();
        return service.getOrder(tenantId, orderId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales:read')")
    public Mono<ResponseEntity<List<SalesOrderResponse>>> listByStatus(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(required = false) SalesOrderStatus status) {
        UUID tenantId = currentUser.tenantId();
        var flux = status != null
                ? service.listByStatus(tenantId, status)
                : service.listPendingDispatch(tenantId);
        return flux.map(SalesOrderResponse::from).collectList().map(ResponseEntity::ok);
    }

    @GetMapping("/by-client/{clientThirdPartyId}")
    @PreAuthorize("hasAuthority('sales:read')")
    public Mono<ResponseEntity<List<SalesOrderResponse>>> listByClient(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID clientThirdPartyId) {
        UUID tenantId = currentUser.tenantId();
        return service.listByClient(tenantId, clientThirdPartyId)
                .map(SalesOrderResponse::from).collectList().map(ResponseEntity::ok);
    }

    @GetMapping("/by-agency/{agencyId}")
    @PreAuthorize("hasAuthority('sales:read')")
    public Mono<ResponseEntity<List<SalesOrderResponse>>> listByAgency(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID agencyId,
            @RequestParam(required = false) String period) {
        UUID tenantId = currentUser.tenantId();
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        return service.listByAgency(tenantId, agencyId, ym)
                .map(SalesOrderResponse::from).collectList().map(ResponseEntity::ok);
    }

    // ─── Lifecycle transitions ────────────────────────────────────────────────

    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> confirm(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID orderId) {
        UUID tenantId = currentUser.tenantId();
        return service.confirmOrder(tenantId, orderId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/reserve-stock")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> reserveStock(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID orderId) {
        UUID tenantId = currentUser.tenantId();
        return service.reserveStock(tenantId, orderId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/dispatch")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> dispatch(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID orderId,
            @Valid @RequestBody DispatchOrderRequest req) {
        UUID tenantId = currentUser.tenantId();
        return service.dispatch(tenantId, orderId, req.missionId())
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/start-delivery")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> startDelivery(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID orderId) {
        UUID tenantId = currentUser.tenantId();
        return service.startDelivery(tenantId, orderId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/deliver")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> deliver(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID orderId) {
        UUID tenantId = currentUser.tenantId();
        return service.markDelivered(tenantId, orderId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/return")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> returnOrder(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID orderId,
            @Valid @RequestBody ReturnOrderRequest req) {
        UUID tenantId = currentUser.tenantId();
        return service.returnOrder(tenantId, orderId, req.reason(), req.note())
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> cancel(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID orderId,
            @RequestBody(required = false) CancelOrderRequest req) {
        UUID tenantId = currentUser.tenantId();
        String reason = req != null ? req.reason() : null;
        return service.cancelOrder(tenantId, orderId, reason)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    @PatchMapping("/{orderId}/link-invoice/{invoiceId}")
    @PreAuthorize("hasAuthority('sales:write')")
    public Mono<ResponseEntity<SalesOrderResponse>> linkInvoice(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID orderId,
            @PathVariable UUID invoiceId) {
        UUID tenantId = currentUser.tenantId();
        return service.linkInvoice(tenantId, orderId, invoiceId)
                .map(o -> ResponseEntity.ok(SalesOrderResponse.from(o)));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private TntAddress toAddress(DeliveryAddressRequest r) {
        return new TntAddress(r.street(), r.quartier(), r.city(), r.country(),
                r.landmark(), r.latitude(), r.longitude(), r.recipientName(), r.recipientPhone());
    }
}
