package com.yowyob.tiibntick.core.notify.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox-backed adapter for publishing notification domain events.
 * Events are consumed by tnt-realtime-core and the audit/monitoring pipeline (ELK).
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * delegates to {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly via {@code KafkaTemplate}. Envelopes are persisted in the same DB
 * transaction as the business write (see the {@code @Transactional} boundary on
 * {@code NotificationService.send}), and {@code OutboxPollerService} relays them to Kafka
 * asynchronously with retry/DLQ — the pre-migration adapter's {@code onErrorResume(...)} swallow
 * (which treated a lost Kafka publish as non-fatal) is removed accordingly: a failure to enqueue
 * the event now fails the whole use case so the notification write rolls back with it.
 *
 * <p>The Kafka wire format is unchanged: same JSON map ({@code eventType}, {@code notificationId},
 * {@code recipientId}, {@code channel}, {@code status}, {@code timestamp}) as message body, and
 * the topic names remain configurable via {@link NotifyProperties} — only the transport changed,
 * so existing consumers require no change.
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaNotificationPublisherAdapter implements IPublishNotificationEventPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationPublisherAdapter.class);

    private static final String AGGREGATE_TYPE = "Notification";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final NotifyProperties properties;
    private final ObjectMapper objectMapper;

    public KafkaNotificationPublisherAdapter(
            PublishEventUseCase publishEventUseCase,
            NotifyProperties properties,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishNotificationSent(Notification notification) {
        return enqueue(properties.getKafkaTopicSent(), notification, "NOTIFICATION_SENT");
    }

    @Override
    public Mono<Void> publishNotificationFailed(Notification notification) {
        return enqueue(properties.getKafkaTopicFailed(), notification, "NOTIFICATION_FAILED");
    }

    private Mono<Void> enqueue(String topic, Notification notification, String eventType) {
        return Mono.fromCallable(() -> buildEventPayload(notification, eventType))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(eventType)
                        .aggregateId(notification.getId().value())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(notification.getTenantId())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(topic)
                        .occurredAt(LocalDateTime.now(ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued event [{}] notificationId={} to outbox",
                        eventType, notification.getId().value()))
                .doOnError(e -> log.error("Failed to enqueue event [{}] notificationId={} to outbox: {}",
                        eventType, notification.getId().value(), e.getMessage()));
    }

    private String buildEventPayload(Notification notification, String eventType) throws Exception {
        Map<String, Object> event = Map.of(
                "eventType", eventType,
                "notificationId", notification.getId().value(),
                "recipientId", notification.getRecipientId(),
                "channel", notification.getChannel().name(),
                "status", notification.getStatus().name(),
                "timestamp", Instant.now().toString());
        return objectMapper.writeValueAsString(event);
    }
}
