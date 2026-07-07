package com.yowyob.tiibntick.core.product.application.port.in;
import com.yowyob.tiibntick.core.product.domain.model.ServiceOffer;
import reactor.core.publisher.Mono;
public interface CreateServiceOfferUseCase {
    Mono<ServiceOffer> createServiceOffer(CreateServiceOfferCommand command);
}
