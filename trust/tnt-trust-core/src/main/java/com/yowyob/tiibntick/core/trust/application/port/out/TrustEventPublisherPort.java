package com.yowyob.tiibntick.core.trust.application.port.out;

import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;

import java.util.List;

/**
 * Outbound Port — {@code TrustEventPublisherPort}.
 *
 * <p>Defines the contract for publishing {@link LogisticTrustEvent} instances
 * to the {@code yow.trust.events} Kafka topic, where they will be consumed
 * by the {@code yow-trust-event} Kernel microservice for Fabric submission.
 *
 * <p>Implemented by {@link com.yowyob.tiibntick.core.trust.adapter.out.messaging.KafkaTrustEventPublisherAdapter}.
 *
 * <h3>Kafka Message Structure</h3>
 * <p>Each message published to {@code yow.trust.events} must conform to the
 * {@code TrustEventKafkaMessage} contract of {@code yow-trust-event}:
 * <ul>
 *   <li>{@code correlationId} — from the logistic event (idempotency)</li>
 *   <li>{@code tenantId} — the tenant</li>
 *   <li>{@code solutionCode} — always "TNT"</li>
 *   <li>{@code eventType} — mapped from {@link LogisticTrustEvent#toKernelEventType()}</li>
 *   <li>{@code entityType} and {@code entityId} — the domain entity</li>
 *   <li>{@code payload} — JSON-serialized logistic context</li>
 *   <li>{@code sourceService} — always "tnt-trust"</li>
 * </ul>
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface TrustEventPublisherPort {

    /**
     * Publishes a single {@link LogisticTrustEvent} to the Kafka trust topic.
     *
     * @param event the logistic trust event to publish
     * @return a {@link Mono} completing when the message is sent to Kafka
     */
    Mono<Void> publish(LogisticTrustEvent event);

    /**
     * Publishes a batch of events. Processed sequentially to respect ordering.
     *
     * @param events the list of logistic trust events to publish
     * @return a {@link Mono} completing when all messages have been sent
     */
    Mono<Void> publishAll(List<LogisticTrustEvent> events);
}
