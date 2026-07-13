package com.yowyob.tiibntick.core.marketback.adapter.out.persistence;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper.ProviderReviewMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository.R2dbcProviderReviewRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IProviderReviewRepository;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrderId;
import com.yowyob.tiibntick.core.marketback.domain.model.ProviderReview;
import com.yowyob.tiibntick.core.marketback.domain.model.ReviewId;
import com.yowyob.tiibntick.core.marketback.domain.model.ReviewStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter — ProviderReview persistence, backed by R2DBC on tnt_market.provider_reviews.
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class ProviderReviewPersistenceAdapter implements IProviderReviewRepository {

    private final R2dbcProviderReviewRepository r2dbcRepository;
    private final ProviderReviewMapper mapper;

    @Override
    public Mono<ProviderReview> save(ProviderReview review) {
        return r2dbcRepository.existsById(review.getId().value())
                .flatMap(exists -> r2dbcRepository.save(mapper.toEntity(review, !exists)))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<ProviderReview> findById(ReviewId id) {
        return r2dbcRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Flux<ProviderReview> findByListingId(MarketListingId listingId) {
        return r2dbcRepository.findByListingId(listingId.value()).map(mapper::toDomain);
    }

    @Override
    public Flux<ProviderReview> findByClientId(UUID clientId, String tenantId) {
        return r2dbcRepository.findByClientIdAndTenantId(clientId, tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<ProviderReview> findPublishedByListingId(MarketListingId listingId) {
        return r2dbcRepository.findByListingIdAndStatus(listingId.value(), ReviewStatus.PUBLISHED.name())
                .map(mapper::toDomain);
    }

    @Override
    public Mono<ProviderReview> findByOrderId(MarketOrderId orderId) {
        return r2dbcRepository.findByOrderId(orderId.value()).map(mapper::toDomain);
    }

    @Override
    public Flux<ProviderReview> findPendingModeration(String tenantId) {
        return r2dbcRepository.findByStatusAndTenantId(ReviewStatus.PENDING_MODERATION.name(), tenantId)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> delete(ReviewId id) {
        return r2dbcRepository.deleteById(id.value());
    }
}
