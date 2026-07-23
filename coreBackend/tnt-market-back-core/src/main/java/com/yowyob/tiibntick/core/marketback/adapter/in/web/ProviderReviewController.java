package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageProviderReviewUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.SubmitReviewCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.ProviderReviewResponse;
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
 * Generic Market API for provider reviews — clients rate providers after a
 * completed order; TNT_ADMIN moderates submissions before they are published.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Market Provider Reviews", description = "Client reviews and ratings for Market providers")
@RestController
@RequestMapping("/api/v1/platform/market/reviews")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ProviderReviewController {

    private final IManageProviderReviewUseCase reviewUseCase;

    @Operation(summary = "Submit a review for a provider following a completed order")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ProviderReviewResponse> submit(@Valid @RequestBody SubmitReviewCommand command) {
        return reviewUseCase.submitReview(command);
    }

    @Operation(summary = "Approve (publish) a pending review")
    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public Mono<ProviderReviewResponse> approve(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return reviewUseCase.approveReview(id, currentUser.userId(), currentUser.tenantId().toString());
    }

    @Operation(summary = "Reject a pending review")
    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public Mono<ProviderReviewResponse> reject(
            @PathVariable UUID id,
            @RequestParam String reason,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return reviewUseCase.rejectReview(id, currentUser.userId(), reason, currentUser.tenantId().toString());
    }

    @Operation(summary = "Flag a published review for re-moderation")
    @PostMapping("/{id}/flag")
    @PreAuthorize("isAuthenticated()")
    public Mono<ProviderReviewResponse> flag(
            @PathVariable UUID id,
            @RequestParam String reason,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return reviewUseCase.flagReview(id, reason, currentUser.tenantId().toString());
    }

    @Operation(summary = "Get a review by id")
    @GetMapping("/{id}")
    public Mono<ProviderReviewResponse> get(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return reviewUseCase.getReview(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "List published reviews for a listing")
    @GetMapping("/by-listing/{listingId}")
    public Flux<ProviderReviewResponse> getByListing(@PathVariable UUID listingId) {
        return reviewUseCase.getPublishedReviewsForListing(listingId);
    }

    @Operation(summary = "List reviews submitted by a client")
    @GetMapping("/by-client/{clientId}")
    public Flux<ProviderReviewResponse> getByClient(
            @PathVariable UUID clientId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return reviewUseCase.getReviewsByClient(clientId, currentUser.tenantId().toString());
    }

    @Operation(summary = "List reviews pending moderation (admin usage)")
    @GetMapping("/pending")
    public Flux<ProviderReviewResponse> getPending(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return reviewUseCase.getPendingModerationReviews(currentUser.tenantId().toString());
    }
}
