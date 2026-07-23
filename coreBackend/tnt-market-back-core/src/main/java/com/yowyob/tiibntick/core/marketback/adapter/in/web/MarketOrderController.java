package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketOrderUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.PlaceMarketOrderCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.PlaceOrderFromQuoteCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.ProcessPaymentCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketOrderResponse;
import com.yowyob.tiibntick.core.marketback.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Generic Market business API for MarketOrder lifecycle management —
 * placement, payment, dispatch, tracking, completion and cancellation.
 *
 * <p>Mechanical port of the standalone tiibntick-market-backend's
 * {@code MarketOrderController}. The original app resolved tenant scoping
 * from an {@code X-Tenant-Id} header (no JWT security); here tenant/actor
 * identity comes from the Kernel-issued JWT via {@code @CurrentUser}, same
 * pattern as {@code NetworkNodeController} in tnt-link-back-core.</p>
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Market Orders", description = "MarketOrder lifecycle: placement, payment, dispatch, delivery, completion")
@RestController
@RequestMapping("/api/v1/platform/market/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MarketOrderController {

    private final IManageMarketOrderUseCase orderUseCase;

    @Operation(summary = "Place a MarketOrder directly from a service offer")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MarketOrderResponse> place(@Valid @RequestBody PlaceMarketOrderCommand command) {
        return orderUseCase.placeOrder(command);
    }

    @Operation(summary = "Place a MarketOrder from an accepted quote (a QuoteRequest with a selected response)")
    @PostMapping("/from-quote")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MarketOrderResponse> placeFromQuote(
            @RequestParam UUID quoteRequestId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID clientId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        PlaceOrderFromQuoteCommand command = new PlaceOrderFromQuoteCommand(
                currentUser.tenantId().toString(), quoteRequestId, clientId);
        return orderUseCase.placeOrderFromQuote(command);
    }

    @Operation(summary = "Process payment for a MarketOrder")
    @PostMapping("/{id}/payment")
    @PreAuthorize("isAuthenticated()")
    public Mono<MarketOrderResponse> processPayment(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessPaymentCommand command,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.processPayment(id, command, currentUser.tenantId().toString());
    }

    @Operation(summary = "Confirm a MarketOrder (DRAFT -> CONFIRMED)")
    @PostMapping("/{id}/confirm")
    @PreAuthorize("isAuthenticated()")
    public Mono<MarketOrderResponse> confirm(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.confirmOrder(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Dispatch a MarketOrder to a delivery mission (PAID -> DISPATCHED)")
    @PostMapping("/{id}/dispatch")
    @PreAuthorize("isAuthenticated()")
    public Mono<MarketOrderResponse> dispatch(
            @PathVariable UUID id,
            @RequestParam String missionId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.dispatchOrder(id, missionId, currentUser.tenantId().toString());
    }

    @Operation(summary = "Mark a MarketOrder in transit (DISPATCHED -> IN_TRANSIT)")
    @PostMapping("/{id}/in-transit")
    @PreAuthorize("isAuthenticated()")
    public Mono<MarketOrderResponse> markInTransit(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.markInTransit(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Mark a MarketOrder delivered (IN_TRANSIT -> DELIVERED)")
    @PostMapping("/{id}/delivered")
    @PreAuthorize("isAuthenticated()")
    public Mono<MarketOrderResponse> markDelivered(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.markDelivered(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Complete a MarketOrder (DELIVERED -> COMPLETED)")
    @PostMapping("/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public Mono<MarketOrderResponse> complete(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.completeOrder(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Cancel a MarketOrder")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Mono<MarketOrderResponse> cancel(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.cancelOrder(id, reason, currentUser.tenantId().toString());
    }

    @Operation(summary = "Get a MarketOrder by id")
    @GetMapping("/{id}")
    public Mono<MarketOrderResponse> get(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.getOrder(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "List MarketOrders placed by a client")
    @GetMapping("/by-client/{clientId}")
    public Flux<MarketOrderResponse> getByClient(
            @PathVariable UUID clientId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.getOrdersByClient(clientId, currentUser.tenantId().toString());
    }

    @Operation(summary = "List MarketOrders received by a provider")
    @GetMapping("/by-provider/{providerId}")
    public Flux<MarketOrderResponse> getByProvider(
            @PathVariable UUID providerId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.getOrdersByProvider(providerId, currentUser.tenantId().toString());
    }

    @Operation(summary = "List MarketOrders by lifecycle status")
    @GetMapping("/by-status/{status}")
    public Flux<MarketOrderResponse> getByStatus(
            @PathVariable OrderStatus status,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return orderUseCase.getOrdersByStatus(status, currentUser.tenantId().toString());
    }
}
