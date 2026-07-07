package com.yowyob.tiibntick.core.product.application.port.out;
import com.yowyob.tiibntick.core.product.domain.model.ServiceOffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.UUID;
public interface ServiceOfferRepository {
    Mono<ServiceOffer> save(ServiceOffer offer);
    Mono<ServiceOffer> findById(UUID offerId);
    Flux<ServiceOffer> findByProvider(UUID tenantId, UUID providerId);
    Flux<ServiceOffer> findPublishedByTenant(UUID tenantId);
    Flux<ServiceOffer> findAllById(List<UUID> offerIds);
    Flux<ServiceOffer> findMatchingForMission(UUID tenantId, double weightKg, double distanceKm);
}
