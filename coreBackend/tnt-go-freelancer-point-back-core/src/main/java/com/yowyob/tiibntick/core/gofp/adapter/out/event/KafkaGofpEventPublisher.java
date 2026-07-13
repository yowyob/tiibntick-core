package com.yowyob.tiibntick.core.gofp.adapter.out.event;

import com.yowyob.tiibntick.core.gofp.application.port.out.IGofpEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaGofpEventPublisher implements IGofpEventPublisher {

    private final KafkaTemplate<String, Object> tntKafkaTemplate;

    private static final String TOPIC_ANNOUNCEMENT_PUBLISHED  = "gofp.announcement.published";
    private static final String TOPIC_DELIVERY_COMPLETED      = "gofp.delivery.completed";
    private static final String TOPIC_SUBSCRIPTION_SUSPENDED  = "gofp.subscription.suspended";

    @Override
    public Mono<Void> publishAnnouncementPublished(UUID announcementId, UUID clientActorId) {
        return Mono.fromRunnable(() -> {
            var payload = Map.of(
                "announcementId", announcementId.toString(),
                "clientActorId",  clientActorId.toString(),
                "eventType",      "ANNOUNCEMENT_PUBLISHED"
            );
            tntKafkaTemplate.send(TOPIC_ANNOUNCEMENT_PUBLISHED, announcementId.toString(), payload);
            log.info("[GOFP] Published ANNOUNCEMENT_PUBLISHED — announcementId={}", announcementId);
        });
    }

    @Override
    public Mono<Void> publishDeliveryCompleted(UUID deliveryId, UUID freelancerActorId, UUID clientActorId) {
        return Mono.fromRunnable(() -> {
            var payload = Map.of(
                "deliveryId",        deliveryId.toString(),
                "freelancerActorId", freelancerActorId.toString(),
                "clientActorId",     clientActorId.toString(),
                "eventType",         "DELIVERY_COMPLETED"
            );
            tntKafkaTemplate.send(TOPIC_DELIVERY_COMPLETED, deliveryId.toString(), payload);
            log.info("[GOFP] Published DELIVERY_COMPLETED — deliveryId={}", deliveryId);
        });
    }

    @Override
    public Mono<Void> publishSubscriptionSuspended(UUID subscriptionId, UUID freelancerActorId) {
        return Mono.fromRunnable(() -> {
            var payload = Map.of(
                "subscriptionId",    subscriptionId.toString(),
                "freelancerActorId", freelancerActorId.toString(),
                "eventType",         "SUBSCRIPTION_SUSPENDED"
            );
            tntKafkaTemplate.send(TOPIC_SUBSCRIPTION_SUSPENDED, subscriptionId.toString(), payload);
            log.info("[GOFP] Published SUBSCRIPTION_SUSPENDED — subscriptionId={}", subscriptionId);
        });
    }
}
