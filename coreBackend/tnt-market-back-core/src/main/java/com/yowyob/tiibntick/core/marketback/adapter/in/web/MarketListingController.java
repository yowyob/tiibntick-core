package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketListingUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateMarketListingCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.UpdateMarketListingCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketListingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller — MarketListing management endpoints (provider showcase,
 * vitrine profile, SEO).
 *
 * <p>Ported from {@code tiibntick-market-backend}'s
 * {@code adapter.inbound.web.controller.MarketListingController}. The
 * original controller resolved {@code tenantId}/moderator identity from
 * {@code X-Tenant-Id}/{@code X-Moderator-Id} request headers; here the
 * calling user's identity is resolved from the Kernel-issued JWT via
 * {@code @CurrentUser TntUserIdentity}, following the same pattern as
 * {@code NetworkNodeController} in tnt-link-back-core.</p>
 *
 * <p>{@code unpublishListing}/{@code deleteListing} were part of the
 * already-ported {@link IManageMarketListingUseCase} but were never wired to
 * a REST route in the original repo; routes are added here so the full use
 * case is reachable.</p>
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Market Listings", description = "Provider showcase (listing), vitrine profile and SEO management on TiiBnTick Market")
@RestController
@RequestMapping("/api/v1/platform/market/listings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MarketListingController {

    private final IManageMarketListingUseCase listingUseCase;

    @Operation(summary = "Create a new MarketListing for the calling tenant")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MarketListingResponse> create(
            @Valid @RequestBody CreateMarketListingCommand request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        CreateMarketListingCommand command = new CreateMarketListingCommand(
                currentUser.tenantId().toString(),
                request.providerId(),
                request.providerType(),
                request.organizationId(),
                request.displayName(),
                request.tagline(),
                request.description(),
                request.contactEmail(),
                request.contactPhone(),
                request.websiteUrl(),
                request.socialLinks(),
                request.certificationIds(),
                request.foundedYear(),
                request.cities(),
                request.radiusKm(),
                request.centerLat(),
                request.centerLng());
        return listingUseCase.createListing(command);
    }

    @Operation(summary = "Update a MarketListing's vitrine profile / coverage zone")
    @PutMapping("/{id}")
    public Mono<MarketListingResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateMarketListingCommand command,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.updateListing(id, command, currentUser.tenantId().toString());
    }

    @Operation(summary = "Submit a DRAFT/REJECTED listing for moderation review")
    @PostMapping("/{id}/publish")
    public Mono<MarketListingResponse> publish(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.submitForReview(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Admin: approve a listing pending review (generates SEO slug, publishes)")
    @PostMapping("/{id}/approve")
    public Mono<MarketListingResponse> approve(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.approveListing(id, currentUser.userId(), currentUser.tenantId().toString());
    }

    @Operation(summary = "Admin: reject a listing pending review with a reason")
    @PostMapping("/{id}/reject")
    public Mono<MarketListingResponse> reject(
            @PathVariable UUID id,
            @RequestParam String reason,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.rejectListing(id, currentUser.userId(), reason, currentUser.tenantId().toString());
    }

    @Operation(summary = "Temporarily remove a published listing from public visibility")
    @PostMapping("/{id}/unpublish")
    public Mono<MarketListingResponse> unpublish(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.unpublishListing(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Admin: suspend a listing for a policy violation")
    @PostMapping("/{id}/suspend")
    public Mono<MarketListingResponse> suspend(
            @PathVariable UUID id,
            @RequestParam String reason,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.suspendListing(id, reason, currentUser.tenantId().toString());
    }

    @Operation(summary = "Get a MarketListing by id")
    @GetMapping("/{id}")
    public Mono<MarketListingResponse> get(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.getListing(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Get a MarketListing by its public SEO slug")
    @GetMapping("/by-slug/{slug}")
    public Mono<MarketListingResponse> getBySlug(
            @PathVariable String slug,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.getListingBySeoSlug(slug, currentUser.tenantId().toString());
    }

    @Operation(summary = "List the listings owned by a given provider")
    @GetMapping("/by-provider/{providerId}")
    public Flux<MarketListingResponse> getByProvider(
            @PathVariable UUID providerId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.getListingsByProvider(providerId, currentUser.tenantId().toString());
    }

    @Operation(summary = "Admin: list listings pending moderation review")
    @GetMapping("/pending-moderation")
    public Flux<MarketListingResponse> getPendingModeration(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.getListingsPendingModeration(currentUser.tenantId().toString());
    }

    @Operation(summary = "Record a public view of the listing (analytics)")
    @PostMapping("/{id}/track-view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> trackView(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.trackView(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Archive (soft-delete) a listing and remove it from the search index")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return listingUseCase.deleteListing(id, currentUser.tenantId().toString());
    }
}
