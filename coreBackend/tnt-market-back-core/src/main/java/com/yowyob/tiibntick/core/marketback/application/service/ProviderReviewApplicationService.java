package com.yowyob.tiibntick.core.marketback.application.service;

import com.yowyob.tiibntick.core.actor.application.command.RateActorCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IRateActorUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageProviderReviewUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.SubmitReviewCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.ProviderReviewResponse;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketEventPublisher;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketListingRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IProviderReviewRepository;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrderId;
import com.yowyob.tiibntick.core.marketback.domain.model.ProviderReview;
import com.yowyob.tiibntick.core.marketback.domain.model.ProviderType;
import com.yowyob.tiibntick.core.marketback.domain.model.Rating;
import com.yowyob.tiibntick.core.marketback.domain.model.ReviewId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Application service — provider review submission and moderation.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderReviewApplicationService implements IManageProviderReviewUseCase {

    private final IProviderReviewRepository reviewRepository;
    private final IMarketListingRepository listingRepository;
    private final IMarketEventPublisher eventPublisher;
    // tnt-actor-core: syncs the review's rating into the provider's real
    // reputation score once a review is published (see #syncProviderRating).
    private final IRateActorUseCase rateActorUseCase;

    // TODO(market-migration): wire tnt-media-core if/when ProviderReview gains
    // a photo-attachment field. SubmitReviewCommand/ProviderReview currently
    // carry no image data, so there is nothing to upload today.

    @Override
    public Mono<ProviderReviewResponse> submitReview(SubmitReviewCommand command) {
        log.debug("Submitting review for provider={} by client={}", command.providerId(), command.clientId());
        return reviewRepository.findByOrderId(MarketOrderId.of(command.orderId()))
                .flatMap(existing -> Mono.<ProviderReview>error(
                        new MarketDomainException("Review already submitted for this order.")))
                .switchIfEmpty(Mono.defer(() -> {
                    Rating rating = new Rating(command.overall(), command.punctuality(),
                            command.communication(), command.packaging(), command.value());
                    ProviderReview review = ProviderReview.create(command.tenantId(), MarketOrderId.of(command.orderId()),
                            command.clientId(), MarketListingId.of(command.listingId()), command.providerId(),
                            rating, command.comment(), command.tags());
                    return reviewRepository.save(review);
                }))
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<ProviderReviewResponse> approveReview(UUID reviewId, UUID adminId, String tenantId) {
        return reviewRepository.findById(ReviewId.of(reviewId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Review not found: " + reviewId)))
                .flatMap(review -> {
                    review.approve(adminId);
                    return reviewRepository.save(review);
                })
                .flatMap(saved -> {
                    List<Object> events = saved.pullDomainEvents();
                    return eventPublisher.publishAll(events, tenantId)
                            .then(updateListingRating(saved.getListingId(), tenantId))
                            .then(syncProviderRating(saved, tenantId))
                            .thenReturn(saved);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<ProviderReviewResponse> rejectReview(UUID reviewId, UUID adminId, String reason, String tenantId) {
        return reviewRepository.findById(ReviewId.of(reviewId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Review not found: " + reviewId)))
                .flatMap(review -> {
                    review.reject(adminId, reason);
                    return reviewRepository.save(review);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<ProviderReviewResponse> flagReview(UUID reviewId, String reason, String tenantId) {
        return reviewRepository.findById(ReviewId.of(reviewId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Review not found: " + reviewId)))
                .flatMap(review -> {
                    review.flag(reason);
                    return reviewRepository.save(review);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<ProviderReviewResponse> getReview(UUID reviewId, String tenantId) {
        return reviewRepository.findById(ReviewId.of(reviewId))
                .switchIfEmpty(Mono.error(new MarketDomainException("Review not found: " + reviewId)))
                .map(this::toResponse);
    }

    @Override
    public Flux<ProviderReviewResponse> getPublishedReviewsForListing(UUID listingId) {
        return reviewRepository.findPublishedByListingId(MarketListingId.of(listingId)).map(this::toResponse);
    }

    @Override
    public Flux<ProviderReviewResponse> getReviewsByClient(UUID clientId, String tenantId) {
        return reviewRepository.findByClientId(clientId, tenantId).map(this::toResponse);
    }

    @Override
    public Flux<ProviderReviewResponse> getPendingModerationReviews(String tenantId) {
        return reviewRepository.findPendingModeration(tenantId).map(this::toResponse);
    }

    /** Recomputes and persists the listing's aggregate rating after a review is published. */
    private Mono<Void> updateListingRating(MarketListingId listingId, String tenantId) {
        return reviewRepository.findPublishedByListingId(listingId)
                .collectList()
                .flatMap(reviews -> {
                    if (reviews.isEmpty()) {
                        return Mono.empty();
                    }
                    double avg = reviews.stream().mapToDouble(r -> r.getRating().average()).average().orElse(0.0);
                    return listingRepository.updateRating(listingId, tenantId, avg, reviews.size());
                });
    }

    /**
     * Best-effort enrichment: pushes the review's average rating into the
     * provider's real reputation score in tnt-actor-core, keeping the actor's
     * profile rating in sync with the reviews recorded on the Market side.
     *
     * <p>The review only carries a raw {@code providerId}; the market's own
     * {@link ProviderType} (resolved from the reviewed listing) is mapped onto
     * actor-core's {@link ActorType} when unambiguous. Agency-backed listings
     * are skipped: their {@code providerId} is an organizationId, not an
     * actor-core actorId, so there is no actor profile to rate.
     *
     * <p>This is pure enrichment — the review itself remains the source of
     * truth, so any failure here (missing listing, unsupported actor type,
     * actor profile not found, ...) is logged and swallowed rather than
     * failing review publication.
     */
    private Mono<Void> syncProviderRating(ProviderReview review, String tenantId) {
        return listingRepository.findById(review.getListingId(), tenantId)
                .flatMap(listing -> {
                    ActorType actorType = toActorType(listing.getProviderType());
                    if (actorType == null) {
                        log.debug("No actor-core ActorType mapping for market ProviderType={}; skipping rating sync for provider={}",
                                listing.getProviderType(), review.getProviderId());
                        return Mono.empty();
                    }
                    RateActorCommand command = new RateActorCommand(
                            UUID.fromString(tenantId), review.getProviderId(), actorType,
                            review.getRating().average(), review.getClientId());
                    return rateActorUseCase.rateActor(command);
                })
                .onErrorResume(e -> {
                    log.warn("Failed to sync provider rating to tnt-actor-core for review={} provider={}: {}",
                            review.getId().value(), review.getProviderId(), e.getMessage());
                    return Mono.empty();
                });
    }

    /** Maps the market module's {@link ProviderType} onto actor-core's {@link ActorType}, when unambiguous. */
    private ActorType toActorType(ProviderType providerType) {
        if (providerType == null) {
            return null;
        }
        return switch (providerType) {
            case FREELANCER -> ActorType.FREELANCER;
            case RELAY_POINT -> ActorType.RELAY_OPERATOR;
            // AGENCY listings carry an organizationId as providerId, not an actor-core
            // actorId — there is no actor profile to rate for this case.
            case AGENCY -> null;
        };
    }

    private ProviderReviewResponse toResponse(ProviderReview review) {
        Rating rating = review.getRating();
        return new ProviderReviewResponse(
                review.getId().value(), review.getListingId().value(),
                review.getOrderId() != null ? review.getOrderId().value() : null,
                review.getClientId(), review.getProviderId(), review.getStatus(),
                rating.overall(), rating.punctuality(), rating.communication(),
                rating.packaging(), rating.value(), rating.average(),
                review.getComment(), review.getTags(), review.getCreatedAt());
    }
}
