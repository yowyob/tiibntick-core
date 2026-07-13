package com.yowyob.tiibntick.core.marketback.application.port.out;

import com.yowyob.tiibntick.core.marketback.domain.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Outbound port — QuoteRequest persistence contract.
 *
 * @author MANFOUO Braun
 */
public interface IQuoteRequestRepository {

    Mono<QuoteRequest> save(QuoteRequest request);

    Mono<QuoteRequest> findById(QuoteRequestId id, String tenantId);

    Flux<QuoteRequest> findByClientId(UUID clientId, String tenantId);

    Flux<QuoteRequest> findByProviderId(UUID providerId, String tenantId);

    Flux<QuoteRequest> findByListingId(MarketListingId listingId);

    Flux<QuoteRequest> findPendingExpired();

    Mono<Void> delete(QuoteRequestId id);
}
