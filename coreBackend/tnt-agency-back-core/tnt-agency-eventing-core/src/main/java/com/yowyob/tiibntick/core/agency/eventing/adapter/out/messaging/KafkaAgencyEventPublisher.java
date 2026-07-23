package com.yowyob.tiibntick.core.agency.eventing.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.domain.event.TntDomainEvent;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Outbox-backed implementation of {@link AgencyEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): delegates to
 * {@link PublishEventUseCase}/{@link PublishEventBatchUseCase} (yow-event-kernel's
 * transactional outbox) instead of sending to Kafka directly and swallowing publish
 * failures ({@code .onErrorResume(e -> Mono.empty())}) — this is the publisher the audit
 * cited by name for "failures are swallowed". Envelopes are now persisted durably and
 * {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ; an
 * outbox enqueue failure propagates to the caller instead of being silently dropped.
 *
 * <p>The Kafka wire format is unchanged: same envelope JSON with
 * {@code {eventId, eventType, aggregateId, aggregateType, tenantId, occurredAt,
 * correlationId, sequence, payload}} as the message body, message key = {@code tenantId},
 * topic routed by {@code eventType} — existing consumers require zero changes.
 */
@Component
public class KafkaAgencyEventPublisher implements AgencyEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAgencyEventPublisher.class);

    private static final Set<String> MISSION_EVENTS = Set.of(
            "MissionCreated", "MissionAssigned", "MissionStarted", "MissionCancelled"
    );
    private static final Set<String> STAFF_EVENTS = Set.of(
            "DelivererRegistered", "ContractSigned", "FreelancerAssociated"
    );

    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final PublishEventBatchUseCase publishEventBatchUseCase;
    private final ObjectMapper objectMapper;
    private final String defaultTopic;
    private final String missionTopic;
    private final String staffTopic;
    private final String contractTopic;

    public KafkaAgencyEventPublisher(
            PublishEventUseCase publishEventUseCase,
            PublishEventBatchUseCase publishEventBatchUseCase,
            ObjectMapper objectMapper,
            @Value("${tnt.kafka.topics.produced.staff-events:" + TntTopics.AGENCY_STAFF_EVENTS + "}")
            String staffTopic,
            @Value("${tnt.kafka.topics.produced.contract-events:" + TntTopics.AGENCY_CONTRACT_EVENTS + "}")
            String contractTopic,
            @Value("${tnt.kafka.topics.produced.mission-request:" + TntTopics.AGENCY_MISSION_REQUEST + "}")
            String missionTopic,
            @Value("${tnt.kafka.topics.produced.domain-events:" + TntTopics.AGENCY_EVENTS + "}")
            String defaultTopic) {
        this.publishEventUseCase = publishEventUseCase;
        this.publishEventBatchUseCase = publishEventBatchUseCase;
        this.objectMapper = objectMapper;
        this.staffTopic = staffTopic;
        this.contractTopic = contractTopic;
        this.missionTopic = missionTopic;
        this.defaultTopic = defaultTopic;
    }

    @Override
    public Mono<Void> publish(TntDomainEvent event) {
        return Mono.fromCallable(() -> toEnvelope(event))
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("[EVENT] enqueued type={} aggregateId={} to outbox",
                        event.getEventType(), event.getAggregateId()))
                .doOnError(e -> log.error("[EVENT] failed to enqueue type={} aggregateId={} to outbox: {}",
                        event.getEventType(), event.getAggregateId(), e.getMessage()));
    }

    @Override
    public Mono<Void> publishAll(List<TntDomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(events)
                .map(this::toEnvelope)
                .collectList()
                .flatMap(publishEventBatchUseCase::publishAll)
                .then();
    }

    // ── Private helpers ───────────────────────────────────────────────

    private DomainEventEnvelope toEnvelope(TntDomainEvent event) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.convertValue(event, Map.class);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.getEventId().toString());
        envelope.put("eventType", event.getEventType());
        envelope.put("aggregateId", event.getAggregateId().toString());
        envelope.put("aggregateType", event.getAggregateType());
        envelope.put("tenantId", event.getTenantId().toString());
        envelope.put("occurredAt", event.getOccurredAt().toString());
        envelope.put("correlationId", event.getCorrelationId());
        envelope.put("sequence", event.getSequenceNumber());
        envelope.put("payload", payload);

        final String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot serialize agency event " + event.getEventType(), e);
        }

        return DomainEventEnvelope.wrap()
                .correlationId(event.getEventId().toString())
                .eventType(event.getEventType())
                .aggregateId(event.getAggregateId().toString())
                .aggregateType(event.getAggregateType())
                .tenantId(event.getTenantId().toString())
                .solutionCode(SOLUTION_CODE)
                .payload(json)
                .kafkaTopic(resolveTopic(event))
                // Preserves the pre-outbox Kafka message key (tenantId) — the envelope
                // would otherwise default the partition key to aggregateId.
                .kafkaPartitionKey(event.getTenantId().toString())
                .occurredAt(LocalDateTime.ofInstant(event.getOccurredAt(), ZoneOffset.UTC))
                .build();
    }

    private String resolveTopic(TntDomainEvent event) {
        String type = event.getEventType();
        if (MISSION_EVENTS.contains(type)) {
            return missionTopic;
        }
        if ("ContractSigned".equals(type)) {
            return contractTopic;
        }
        if (STAFF_EVENTS.contains(type)) {
            return staffTopic;
        }
        return defaultTopic;
    }
}
