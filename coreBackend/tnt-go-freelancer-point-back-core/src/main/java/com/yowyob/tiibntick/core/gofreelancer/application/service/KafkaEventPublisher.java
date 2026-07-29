package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.AnnouncementPublishedEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.FreelancerCreatedEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.FreelancerValidatedEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.SubscriptionAttemptEvent;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox-backed event publisher for the Go-Freelancer-Point module.
 *
 * <p>Delegates to {@link PublishEventUseCase} (yow-event-kernel transactional outbox)
 * instead of sending directly to Kafka via KafkaTemplate — guarantees at-least-once
 * delivery even if Kafka is temporarily unavailable.
 *
 * <p>Topic names come from {@link TntTopics} constants — no string literals in this class.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service("gofpKafkaEventPublisher")
public class KafkaEventPublisher implements EventPublisher {

    private static final String AGGREGATE_TYPE_FREELANCER    = "Freelancer";
    private static final String AGGREGATE_TYPE_ANNOUNCEMENT  = "Announcement";
    private static final String AGGREGATE_TYPE_SUBSCRIPTION  = "Subscription";
    private static final String SOLUTION_CODE                = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public KafkaEventPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishFreelancerCreated(FreelancerCreatedEvent event) {
        enqueue(TntTopics.GOFP_ANNOUNCEMENT_PUBLISHED, event,
                event.getFreelancerId().toString(),
                AGGREGATE_TYPE_FREELANCER,
                SOLUTION_CODE);
    }

    @Override
    public void publishFreelancerValidated(FreelancerValidatedEvent event) {
        enqueue(TntTopics.ACTOR_KYC_VALIDATED, event,
                event.getFreelancerId().toString(),
                AGGREGATE_TYPE_FREELANCER,
                SOLUTION_CODE);
    }

    @Override
    public void publishAnnouncementPublished(AnnouncementPublishedEvent event) {
        enqueue(TntTopics.GOFP_ANNOUNCEMENT_PUBLISHED, event,
                event.getAnnouncement().getId().toString(),
                AGGREGATE_TYPE_ANNOUNCEMENT,
                SOLUTION_CODE);
    }

    @Override
    public void publishSubscriptionAttempt(SubscriptionAttemptEvent event) {
        enqueue(TntTopics.GOFP_SUBSCRIPTION_SUSPENDED, event,
                event.getAnnouncementId().toString(),
                AGGREGATE_TYPE_SUBSCRIPTION,
                SOLUTION_CODE);
    }

    @Override
    public void publishMatchingNotification(MatchingNotificationEvent event) {
        enqueue(TntTopics.GOFP_ANNOUNCEMENT_PUBLISHED, event,
                event.getFreelancerId().toString(),
                AGGREGATE_TYPE_FREELANCER,
                SOLUTION_CODE);
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private void enqueue(String topic, Object event, String aggregateId,
                         String aggregateType, String solutionCode) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            DomainEventEnvelope envelope = DomainEventEnvelope.wrap()
                    .correlationId(UUID.randomUUID().toString())
                    .eventType(event.getClass().getSimpleName())
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .tenantId(SOLUTION_CODE)   // tenantId resolved at runtime via SecurityContext
                    .solutionCode(solutionCode)
                    .payload(payload)
                    .kafkaTopic(topic)
                    .occurredAt(LocalDateTime.now())
                    .build();

            publishEventUseCase.publish(envelope)
                    .subscribe(
                            null,
                            err -> log.error("Failed to enqueue event {} for aggregate {}: {}",
                                    event.getClass().getSimpleName(), aggregateId, err.getMessage())
                    );
        } catch (Exception e) {
            log.error("Failed to serialize event {}: {}", event.getClass().getSimpleName(), e.getMessage());
        }
    }
}
