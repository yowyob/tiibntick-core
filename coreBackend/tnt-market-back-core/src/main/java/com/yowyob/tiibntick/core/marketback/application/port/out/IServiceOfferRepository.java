package com.yowyob.tiibntick.core.marketback.application.port.out;

import com.yowyob.tiibntick.core.marketback.domain.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port — ServiceOffer persistence contract.
 * @author MANFOUO Braun
 */
public interface IServiceOfferRepository {
    Mono<ServiceOffer> save(ServiceOffer offer);
    Mono<ServiceOffer> findById(ServiceOfferId id);
    Flux<ServiceOffer> findByListingId(MarketListingId listingId);
    Flux<ServiceOffer> findActiveByListingId(MarketListingId listingId);
    Flux<ServiceOffer> findByServiceType(ServiceType type, String tenantId);
    Flux<ServiceOffer> findByPriceRange(long minPriceXaf, long maxPriceXaf, String tenantId);
    Mono<Void> delete(ServiceOfferId id);
}
