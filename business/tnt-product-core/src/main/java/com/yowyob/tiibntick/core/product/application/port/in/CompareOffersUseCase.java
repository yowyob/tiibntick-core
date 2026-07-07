package com.yowyob.tiibntick.core.product.application.port.in;
import com.yowyob.tiibntick.core.product.domain.model.OfferComparison;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.UUID;
public interface CompareOffersUseCase {
    Mono<OfferComparison> compareOffers(List<UUID> offerIds);
}
