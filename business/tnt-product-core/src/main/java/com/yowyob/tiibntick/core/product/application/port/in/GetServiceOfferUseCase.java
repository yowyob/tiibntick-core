package com.yowyob.tiibntick.core.product.application.port.in;
import com.yowyob.tiibntick.core.product.domain.model.ServiceOffer;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface GetServiceOfferUseCase {
    Mono<ServiceOffer> getServiceOffer(UUID offerId);
}
