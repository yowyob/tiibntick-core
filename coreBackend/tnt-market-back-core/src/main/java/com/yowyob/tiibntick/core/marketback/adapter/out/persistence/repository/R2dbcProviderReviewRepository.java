package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.ProviderReviewEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC repository for ProviderReview.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcProviderReviewRepository extends ReactiveCrudRepository<ProviderReviewEntity, UUID> {

    Flux<ProviderReviewEntity> findByListingId(UUID listingId);

    Flux<ProviderReviewEntity> findByListingIdAndStatus(UUID listingId, String status);

    Flux<ProviderReviewEntity> findByClientIdAndTenantId(UUID clientId, String tenantId);

    Mono<ProviderReviewEntity> findByOrderId(UUID orderId);

    Flux<ProviderReviewEntity> findByStatusAndTenantId(String status, String tenantId);
}
