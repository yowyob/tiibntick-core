package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.QuoteRequestEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC repository for {@link QuoteRequestEntity} — {@code tnt_market.quote_requests}.
 *
 * <p>Ported from {@code tiibntick-market-backend}'s
 * {@code adapter.outbound.persistence.repository.R2dbcQuoteRequestRepository}.</p>
 *
 * @author MANFOUO Braun
 */
public interface R2dbcQuoteRequestRepository extends ReactiveCrudRepository<QuoteRequestEntity, UUID> {

    Mono<QuoteRequestEntity> findByIdAndTenantId(UUID id, String tenantId);

    Flux<QuoteRequestEntity> findByClientIdAndTenantId(UUID clientId, String tenantId);

    Flux<QuoteRequestEntity> findByProviderIdAndTenantId(UUID providerId, String tenantId);

    Flux<QuoteRequestEntity> findByListingId(UUID listingId);

    Flux<QuoteRequestEntity> findByStatusAndExpiresAtBefore(String status, LocalDateTime now);
}
