package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.domain.event.*;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox-backed adapter implementing {@link IWalletEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * delegates to {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly via {@code KafkaTemplate.send(...)}. Envelopes are persisted in the
 * same DB transaction as the wallet/payment write (see the {@code @Transactional} boundaries in
 * {@code WalletService}), and {@code OutboxPollerService} relays them to Kafka asynchronously
 * with retry/DLQ — a financial write can no longer succeed while its event is silently lost.
 *
 * <p>The Kafka wire format is unchanged: the message value is still exactly
 * {@code objectMapper.writeValueAsString(event)} — the raw serialised domain event — the
 * message key is unchanged per event type (paymentIntentId / walletId / delivererId, now
 * carried as {@link DomainEventEnvelope#getKafkaPartitionKey()} via {@code aggregateId}),
 * and the topic naming convention {@code tnt.billing.wallet.{event-type}} is untouched, so
 * existing consumers require no change.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class WalletKafkaPublisher implements IWalletEventPublisher {

    private static final String TOPIC_PAYMENT_INITIATED = TntTopics.BILLING_WALLET_PAYMENT_INITIATED;
    private static final String TOPIC_PAYMENT_CONFIRMED = TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED;
    private static final String TOPIC_PAYMENT_FAILED = TntTopics.BILLING_WALLET_PAYMENT_FAILED;
    private static final String TOPIC_WALLET_CREDITED = TntTopics.BILLING_WALLET_WALLET_CREDITED;
    private static final String TOPIC_WALLET_DEBITED = TntTopics.BILLING_WALLET_WALLET_DEBITED;
    private static final String TOPIC_COMMISSION_CALCULATED = TntTopics.BILLING_WALLET_COMMISSION_CALCULATED;
    private static final String TOPIC_SPLIT_EXECUTED = TntTopics.BILLING_WALLET_SPLIT_EXECUTED;

    private static final String AGGREGATE_TYPE = "Wallet";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public WalletKafkaPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("walletObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(PaymentInitiated event) {
        return publishEvent(TOPIC_PAYMENT_INITIATED,
                event.paymentIntentId().toString(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publish(PaymentConfirmed event) {
        return publishEvent(TOPIC_PAYMENT_CONFIRMED,
                event.paymentIntentId().toString(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publish(PaymentFailed event) {
        return publishEvent(TOPIC_PAYMENT_FAILED,
                event.paymentIntentId().toString(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publish(WalletCredited event) {
        return publishEvent(TOPIC_WALLET_CREDITED,
                event.walletId().toString(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publish(WalletDebited event) {
        return publishEvent(TOPIC_WALLET_DEBITED,
                event.walletId().toString(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publish(CommissionCalculated event) {
        return publishEvent(TOPIC_COMMISSION_CALCULATED,
                event.delivererId().toString(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publish(WalletSplitExecuted event) {
        return publishEvent(TOPIC_SPLIT_EXECUTED,
                event.freelancerOrgId(), event.tenantId(), event.occurredAt(), event);
    }

    private Mono<Void> publishEvent(String topic, String key, UUID tenantId,
                                    LocalDateTime occurredAt, Object event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(key)
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(tenantId.toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(topic)
                        .occurredAt(occurredAt)
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued event={} to outbox topic={} key={}",
                        event.getClass().getSimpleName(), topic, key))
                .doOnError(e -> log.error("Failed to enqueue event={} to outbox topic={}: {}",
                        event.getClass().getSimpleName(), topic, e.getMessage()))
                .onErrorResume(JsonProcessingException.class, e -> {
                    log.error("Serialization error for event {}: {}",
                            event.getClass().getSimpleName(), e.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}
