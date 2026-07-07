package com.yowyob.tiibntick.core.product.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.product.application.port.out.ProductEventPublisherPort;
import com.yowyob.tiibntick.core.product.domain.event.ProductCreatedEvent;
import com.yowyob.tiibntick.core.product.domain.event.ServiceOfferPublishedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Component
public class ProductKafkaEventPublisher implements ProductEventPublisherPort {

    private static final String PRODUCT_CREATED_TOPIC = "tnt.product.created";
    private static final String OFFER_PUBLISHED_TOPIC = "tnt.product.offer.published";

    private final KafkaSender<String, String> kafkaSender;
    private final ObjectMapper objectMapper;

    public ProductKafkaEventPublisher(KafkaSender<String, String> kafkaSender,
                                      @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.kafkaSender = kafkaSender;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishProductCreated(ProductCreatedEvent event) {
        return serialize(event)
                .flatMap(payload -> send(PRODUCT_CREATED_TOPIC, event.productId().toString(), payload));
    }

    @Override
    public Mono<Void> publishServiceOfferPublished(ServiceOfferPublishedEvent event) {
        return serialize(event)
                .flatMap(payload -> send(OFFER_PUBLISHED_TOPIC, event.offerId().toString(), payload));
    }

    private Mono<String> serialize(Object event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event));
    }

    private Mono<Void> send(String topic, String key, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        SenderRecord<String, String, String> senderRecord = SenderRecord.create(record, key);
        return kafkaSender.send(Mono.just(senderRecord)).then();
    }
}
