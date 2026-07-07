package com.yowyob.tiibntick.core.product.application.port.out;
import com.yowyob.tiibntick.core.product.domain.event.ProductCreatedEvent;
import com.yowyob.tiibntick.core.product.domain.event.ServiceOfferPublishedEvent;
import reactor.core.publisher.Mono;
public interface ProductEventPublisherPort {
    Mono<Void> publishProductCreated(ProductCreatedEvent event);
    Mono<Void> publishServiceOfferPublished(ServiceOfferPublishedEvent event);
}
