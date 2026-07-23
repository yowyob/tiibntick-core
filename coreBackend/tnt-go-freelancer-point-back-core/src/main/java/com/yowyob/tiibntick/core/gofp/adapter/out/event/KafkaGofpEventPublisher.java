package com.yowyob.tiibntick.core.gofp.adapter.out.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.gofp.application.port.out.IGofpEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox-backed implementation of {@link IGofpEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): delegates to
 * {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * fire-and-forgetting {@code KafkaTemplate.send(...)} whose result was never awaited.
 * Envelopes are persisted in the same DB transaction as the business write (see the
 * {@code @Transactional} boundaries in the calling services), and
 * {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ.
 *
 * <p>The Kafka wire format is unchanged: same flat JSON payloads (e.g.
 * {@code {announcementId, clientActorId, eventType}}), same message key (the event's own
 * aggregate id), same {@code gofp.*} topics (now referenced via {@link TntTopics}).
 *
 * <p>GOFP data is actor-scoped, not tenant-scoped — no event here carries a tenant id
 * (see the port signatures). The outbox envelope requires one, so the fixed marker
 * {@link #GOFP_TENANT_MARKER} is used, consistent with the module-wide single-scope
 * semantics of the {@code gofp.*} topics.
 */
@Slf4j
@Component
public class KafkaGofpEventPublisher implements IGofpEventPublisher {

    static final String TOPIC_ANNOUNCEMENT_PUBLISHED = TntTopics.GOFP_ANNOUNCEMENT_PUBLISHED;
    static final String TOPIC_DELIVERY_COMPLETED     = TntTopics.GOFP_DELIVERY_COMPLETED;
    static final String TOPIC_SUBSCRIPTION_SUSPENDED = TntTopics.GOFP_SUBSCRIPTION_SUSPENDED;

    /** GOFP events are actor-scoped; the envelope's mandatory tenant field carries this marker. */
    static final String GOFP_TENANT_MARKER = "gofp";

    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public KafkaGofpEventPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishAnnouncementPublished(UUID announcementId, UUID clientActorId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("announcementId", announcementId.toString());
        payload.put("clientActorId", clientActorId != null ? clientActorId.toString() : null);
        payload.put("eventType", "ANNOUNCEMENT_PUBLISHED");
        return enqueue("ANNOUNCEMENT_PUBLISHED", "Announcement", announcementId,
                TOPIC_ANNOUNCEMENT_PUBLISHED, payload);
    }

    @Override
    public Mono<Void> publishDeliveryCompleted(UUID deliveryId, UUID freelancerActorId, UUID clientActorId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deliveryId", deliveryId.toString());
        payload.put("freelancerActorId", freelancerActorId != null ? freelancerActorId.toString() : null);
        // clientActorId is legitimately null on this path (resolved downstream via the
        // announcement) — the previous Map.of(...) based payload NPE'd on it.
        payload.put("clientActorId", clientActorId != null ? clientActorId.toString() : null);
        payload.put("eventType", "DELIVERY_COMPLETED");
        return enqueue("DELIVERY_COMPLETED", "Delivery", deliveryId,
                TOPIC_DELIVERY_COMPLETED, payload);
    }

    @Override
    public Mono<Void> publishSubscriptionSuspended(UUID subscriptionId, UUID freelancerActorId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subscriptionId", subscriptionId.toString());
        payload.put("freelancerActorId", freelancerActorId != null ? freelancerActorId.toString() : null);
        payload.put("eventType", "SUBSCRIPTION_SUSPENDED");
        return enqueue("SUBSCRIPTION_SUSPENDED", "Subscription", subscriptionId,
                TOPIC_SUBSCRIPTION_SUSPENDED, payload);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private Mono<Void> enqueue(String eventType, String aggregateType, UUID aggregateId,
                               String topic, Map<String, Object> payload) {
        return Mono.fromCallable(() -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(eventType)
                        .aggregateId(aggregateId.toString())
                        .aggregateType(aggregateType)
                        .tenantId(GOFP_TENANT_MARKER)
                        .solutionCode(SOLUTION_CODE)
                        .payload(objectMapper.writeValueAsString(payload))
                        .kafkaTopic(topic)
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.info("[GOFP] Enqueued {} — aggregateId={} to outbox",
                        eventType, aggregateId))
                .doOnError(e -> log.error("[GOFP] Failed to enqueue {} — aggregateId={}: {}",
                        eventType, aggregateId, e.getMessage()));
    }
}
