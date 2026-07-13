package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageQuoteRequestUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateQuoteRequestCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.SubmitQuoteResponseCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.QuoteRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * Generic Market QuoteRequest API — client-to-provider quote requests
 * ("demande de devis") and provider responses. Single entry point the Market
 * BFF calls to power the negotiation flow: request &rarr; provider
 * response(s) &rarr; client selection &rarr; (later) conversion to a
 * MarketOrder.
 *
 * <p>Ported from {@code tiibntick-market-backend}'s
 * {@code adapter.inbound.web.controller.QuoteRequestController}. Tenant and
 * client identity now come from the Kernel-issued JWT via {@link CurrentUser}
 * instead of the original's {@code X-Tenant-Id}/{@code X-Client-Id} headers —
 * same trusted-identity pattern as {@code NetworkNodeController} in
 * tnt-link-back-core.</p>
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Market Quote Requests", description = "Client-to-provider quote requests (demande de devis) and provider responses")
@RestController
@RequestMapping("/api/v1/platform/market/quote-requests")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class QuoteRequestController {

    private final IManageQuoteRequestUseCase quoteUseCase;

    @Operation(summary = "Create a quote request from a client to a provider listing")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<QuoteRequestResponse> create(
            @RequestBody CreateQuoteRequestCommand request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID clientId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        CreateQuoteRequestCommand command = new CreateQuoteRequestCommand(
                currentUser.tenantId().toString(), clientId, request.listingId(), request.providerId(),
                request.pickupStreet(), request.pickupDistrict(), request.pickupCity(),
                request.pickupLat(), request.pickupLng(),
                request.deliveryStreet(), request.deliveryDistrict(), request.deliveryCity(),
                request.deliveryLat(), request.deliveryLng(),
                request.parcelDescription(), request.weightKg(),
                request.lengthCm(), request.widthCm(), request.heightCm(), request.valueXaf(),
                request.fragile(), request.perishable(), request.requiresInsurance(), request.quantity(),
                request.desiredPickupAt(), request.desiredDeliveryAt(), request.urgency(),
                request.specialInstructions(), request.notes());
        return quoteUseCase.createQuoteRequest(command);
    }

    @Operation(summary = "Provider submits a response (offer) to a quote request")
    @PostMapping("/{id}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<QuoteRequestResponse> respond(
            @PathVariable UUID id,
            @RequestBody SubmitQuoteResponseCommand command,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return quoteUseCase.submitQuoteResponse(id, command, currentUser.tenantId().toString());
    }

    @Operation(summary = "Client selects one of the provider responses")
    @PostMapping("/{id}/responses/{responseId}/select")
    public Mono<QuoteRequestResponse> selectResponse(
            @PathVariable UUID id,
            @PathVariable UUID responseId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID clientId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return quoteUseCase.selectQuoteResponse(id, responseId, clientId, currentUser.tenantId().toString());
    }

    @Operation(summary = "Client rejects one of the provider responses")
    @PostMapping("/{id}/responses/{responseId}/reject")
    public Mono<QuoteRequestResponse> rejectResponse(
            @PathVariable UUID id,
            @PathVariable UUID responseId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID clientId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return quoteUseCase.rejectQuoteResponse(id, responseId, clientId, currentUser.tenantId().toString());
    }

    @Operation(summary = "Cancel a quote request")
    @PostMapping("/{id}/cancel")
    public Mono<QuoteRequestResponse> cancel(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID clientId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return quoteUseCase.cancelQuoteRequest(id, reason, clientId, currentUser.tenantId().toString());
    }

    @Operation(summary = "Get a quote request by id")
    @GetMapping("/{id}")
    public Mono<QuoteRequestResponse> get(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return quoteUseCase.getQuoteRequest(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "List quote requests submitted by a client")
    @GetMapping("/by-client/{clientId}")
    public Flux<QuoteRequestResponse> getByClient(
            @PathVariable UUID clientId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return quoteUseCase.getClientQuoteRequests(clientId, currentUser.tenantId().toString());
    }

    @Operation(summary = "List quote request leads received by a provider")
    @GetMapping("/by-provider/{providerId}")
    public Flux<QuoteRequestResponse> getByProvider(
            @PathVariable UUID providerId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return quoteUseCase.getProviderLeads(providerId, currentUser.tenantId().toString());
    }
}
