package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.domain.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * WalletKafkaPublisher — publishes billing wallet domain events to Kafka topics.
 *
 * Topic naming convention: tnt.billing.wallet.{event-type}
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletKafkaPublisher implements IWalletEventPublisher {

    private static final String TOPIC_PAYMENT_INITIATED = "tnt.billing.wallet.payment-initiated";
    private static final String TOPIC_PAYMENT_CONFIRMED = "tnt.billing.wallet.payment-confirmed";
    private static final String TOPIC_PAYMENT_FAILED = "tnt.billing.wallet.payment-failed";
    private static final String TOPIC_WALLET_CREDITED = "tnt.billing.wallet.wallet-credited";
    private static final String TOPIC_WALLET_DEBITED = "tnt.billing.wallet.wallet-debited";
    private static final String TOPIC_COMMISSION_CALCULATED = "tnt.billing.wallet.commission-calculated";

    private final @Qualifier("walletKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate;
    private final @Qualifier("walletObjectMapper") ObjectMapper objectMapper;

    @Override
    public Mono<Void> publish(PaymentInitiated event) {
        return publishEvent(TOPIC_PAYMENT_INITIATED,
                event.paymentIntentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(PaymentConfirmed event) {
        return publishEvent(TOPIC_PAYMENT_CONFIRMED,
                event.paymentIntentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(PaymentFailed event) {
        return publishEvent(TOPIC_PAYMENT_FAILED,
                event.paymentIntentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(WalletCredited event) {
        return publishEvent(TOPIC_WALLET_CREDITED, event.walletId().toString(), event);
    }

    @Override
    public Mono<Void> publish(WalletDebited event) {
        return publishEvent(TOPIC_WALLET_DEBITED, event.walletId().toString(), event);
    }

    @Override
    public Mono<Void> publish(CommissionCalculated event) {
        return publishEvent(TOPIC_COMMISSION_CALCULATED,
                event.delivererId().toString(), event);
    }

    private Mono<Void> publishEvent(String topic, String key, Object event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(payload -> Mono.fromFuture(
                        kafkaTemplate.send(topic, key, payload).toCompletableFuture()))
                .doOnSuccess(result -> log.debug("Event published to topic={} key={}", topic, key))
                .doOnError(e -> log.error("Failed to publish event to topic={}: {}", topic, e.getMessage()))
                .onErrorResume(JsonProcessingException.class, e -> {
                    log.error("Serialization error for event {}: {}", event.getClass().getSimpleName(), e.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}
