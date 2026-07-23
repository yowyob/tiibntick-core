package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageServiceOfferUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateServiceOfferCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.SimulatePriceCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.UpdateServiceOfferCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.PriceSimulationResponse;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.ServiceOfferResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Generic Market ServiceOffer management API — pricing rules, handling
 * constraints and availability schedule for a provider's listing. Single
 * entry point the Market BFF calls to manage service offers.
 * Ported from the standalone app's {@code MarketServiceOfferController}.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Market Service Offers", description = "Pricing, constraints and availability for provider service offers")
@RestController
@RequestMapping("/api/v1/platform/market/service-offers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MarketServiceOfferController {

    private final IManageServiceOfferUseCase offerUseCase;

    @Operation(summary = "Create a service offer for a listing")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ServiceOfferResponse> create(
            @Valid @RequestBody CreateServiceOfferCommand command,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        // tenantId is always derived from the authenticated caller, never trusted from the body.
        CreateServiceOfferCommand secured = new CreateServiceOfferCommand(
                currentUser.tenantId().toString(), command.listingId(), command.providerId(),
                command.name(), command.description(), command.serviceType(),
                command.basePriceXaf(), command.perKmRateXaf(), command.perKgRateXaf(),
                command.minimumPriceXaf(), command.maximumPriceXaf(), command.pricingDslExpression(),
                command.maxWeightKg(), command.maxLengthCm(), command.maxWidthCm(), command.maxHeightCm(),
                command.maxValueXaf(), command.acceptsFragile(), command.acceptsPerishable(), command.maxDistanceKm(),
                command.daysOfWeek(), command.openTime(), command.closeTime(),
                command.expressAvailable(), command.sameDayAvailable());
        return offerUseCase.createOffer(secured);
    }

    @Operation(summary = "Update an existing service offer")
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Mono<ServiceOfferResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateServiceOfferCommand command,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return offerUseCase.updateOffer(id, command, currentUser.tenantId().toString());
    }

    @Operation(summary = "Activate a service offer so it becomes visible to clients")
    @PostMapping("/{id}/activate")
    @PreAuthorize("isAuthenticated()")
    public Mono<ServiceOfferResponse> activate(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return offerUseCase.activateOffer(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Temporarily deactivate a service offer")
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("isAuthenticated()")
    public Mono<ServiceOfferResponse> deactivate(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return offerUseCase.deactivateOffer(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Permanently archive a service offer")
    @PostMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    public Mono<ServiceOfferResponse> archive(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return offerUseCase.archiveOffer(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Get a service offer by id")
    @GetMapping("/{id}")
    public Mono<ServiceOfferResponse> get(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return offerUseCase.getOffer(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "List all service offers for a listing")
    @GetMapping("/by-listing/{listingId}")
    public Flux<ServiceOfferResponse> getByListing(
            @PathVariable UUID listingId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return offerUseCase.getOffersByListing(listingId, currentUser.tenantId().toString());
    }

    @Operation(summary = "List active service offers for a listing")
    @GetMapping("/by-listing/{listingId}/active")
    public Flux<ServiceOfferResponse> getActiveByListing(
            @PathVariable UUID listingId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return offerUseCase.getActiveOffersByListing(listingId, currentUser.tenantId().toString());
    }

    @Operation(summary = "Simulate delivery cost for a given parcel and route on this offer")
    @PostMapping("/{id}/simulate-price")
    @PreAuthorize("isAuthenticated()")
    public Mono<PriceSimulationResponse> simulatePrice(
            @PathVariable UUID id,
            @Valid @RequestBody SimulatePriceCommand command,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return offerUseCase.simulatePrice(id, command, currentUser.tenantId().toString());
    }
}
