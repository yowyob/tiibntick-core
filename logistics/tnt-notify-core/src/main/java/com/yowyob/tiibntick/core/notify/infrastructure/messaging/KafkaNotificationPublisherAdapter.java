package com.yowyob.tiibntick.core.notify.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Map;

/**
 * Kafka adapter for publishing notification domain events.
 * Events are consumed by tnt-realtime-core and the audit/monitoring pipeline
 * (ELK).
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaNotificationPublisherAdapter implements IPublishNotificationEventPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationPublisherAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final NotifyProperties properties;
    private final ObjectMapper objectMapper;

    public KafkaNotificationPublisherAdapter(
            @Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            NotifyProperties properties,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishNotificationSent(Notification notification) {
        return publishEvenement(properties.getKafkaTopicSent(), notification, "NOTIFICATION_SENT");
    }

    @Override
    public Mono<Void> publishNotificationFailed(Notification notification) {
        return publishEvenement(properties.getKafkaTopicFailed(), notification, "NOTIFICATION_FAILED");
    }

    private Mono<Void> publishEvenement(String topic, Notification notification, String eventType) {
        return Mono.fromCallable(() -> buildEventPayload(notification, eventType))
                .flatMap(payload -> Mono.fromFuture(
                        kafkaTemplate.send(topic, notification.getId().value(), payload)))
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .doOnSuccess(v -> log.debug("Kafka event [{}] published to topic {}", eventType, topic))
                .onErrorResume(e -> {
                    log.error("Failed to publish Kafka event [{}]: {}", eventType, e.getMessage());
                    return Mono.empty(); // Non-fatal — notification was already processed
                });
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
