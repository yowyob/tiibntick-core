package com.yowyob.tiibntick.core.billing.invoice.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoiceEventPublisher;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceCancelled;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceGenerated;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoicePaid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Kafka adapter for publishing invoice domain events.
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaInvoiceEventPublisher implements InvoiceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaInvoiceEventPublisher.class);
    private static final String TOPIC = "tnt.billing.invoice.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaInvoiceEventPublisher(
            @Qualifier("invoiceKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("invoiceObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(Object event) {
        return Mono.fromRunnable(() -> {
            try {
                String key   = resolveKey(event);
                String value = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(TOPIC, key, value);
                log.debug("Published invoice event: {} key={}", event.getClass().getSimpleName(), key);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize invoice event: {}", e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> publishAll(List<Object> events) {
        return Mono.fromRunnable(() -> events.forEach(e -> publish(e).subscribe()))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }

    private String resolveKey(Object event) {
        return switch (event) {
            case InvoiceGenerated e  -> e.invoiceId().toString();
            case InvoicePaid e       -> e.invoiceId().toString();
            case InvoiceCancelled e  -> e.invoiceId().toString();
            default -> "unknown";
        };
    }
}
