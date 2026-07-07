package com.yowyob.tiibntick.core.product.application.port.in;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface PublishServiceOfferUseCase {
    Mono<Void> publishToMarket(UUID offerId);
    Mono<Void> unpublishFromMarket(UUID offerId);
}
