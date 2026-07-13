package com.yowyob.tiibntick.core.marketback.application.port.in;

import com.yowyob.tiibntick.core.marketback.application.port.in.command.*;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port — ServiceOffer management use cases.
 * @author MANFOUO Braun
 */
public interface IManageServiceOfferUseCase {

    Mono<ServiceOfferResponse> createOffer(CreateServiceOfferCommand command);
    Mono<ServiceOfferResponse> updateOffer(UUID offerId, UpdateServiceOfferCommand command, String tenantId);
    Mono<ServiceOfferResponse> activateOffer(UUID offerId, String tenantId);
    Mono<ServiceOfferResponse> deactivateOffer(UUID offerId, String tenantId);
    Mono<ServiceOfferResponse> archiveOffer(UUID offerId, String tenantId);
    Mono<ServiceOfferResponse> getOffer(UUID offerId, String tenantId);
    Flux<ServiceOfferResponse> getOffersByListing(UUID listingId, String tenantId);
    Flux<ServiceOfferResponse> getActiveOffersByListing(UUID listingId, String tenantId);
    Mono<PriceSimulationResponse> simulatePrice(UUID offerId, SimulatePriceCommand command, String tenantId);
}
