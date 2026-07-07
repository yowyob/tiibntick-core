package com.yowyob.tiibntick.core.product.application.port.in;
import com.yowyob.tiibntick.core.product.domain.model.ServiceOffer;
import reactor.core.publisher.Flux;
import java.util.UUID;
public interface FindMatchingOffersUseCase {
    Flux<ServiceOffer> findMatchingOffers(UUID tenantId, double weightKg, double distanceKm);
}
