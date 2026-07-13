package com.yowyob.tiibntick.core.marketback.application.port.in;

import com.yowyob.tiibntick.core.marketback.application.port.in.command.*;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port — ProviderReview use cases.
 * @author MANFOUO Braun
 */
public interface IManageProviderReviewUseCase {

    Mono<ProviderReviewResponse> submitReview(SubmitReviewCommand command);
    Mono<ProviderReviewResponse> approveReview(UUID reviewId, UUID adminId, String tenantId);
    Mono<ProviderReviewResponse> rejectReview(UUID reviewId, UUID adminId, String reason, String tenantId);
    Mono<ProviderReviewResponse> flagReview(UUID reviewId, String reason, String tenantId);
    Mono<ProviderReviewResponse> getReview(UUID reviewId, String tenantId);
    Flux<ProviderReviewResponse> getPublishedReviewsForListing(UUID listingId);
    Flux<ProviderReviewResponse> getReviewsByClient(UUID clientId, String tenantId);
    Flux<ProviderReviewResponse> getPendingModerationReviews(String tenantId);
}
