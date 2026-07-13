package com.yowyob.tiibntick.core.agency.eventing.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.domain.event.TntDomainEvent;
import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kafka implementation of {@link AgencyEventPublisher}.
 *
 * <p>Byte-faithful port of the monolith {@code OutboxEventPublisher}: same envelope with a nested
 * {@code payload}, message key = {@code tenantId}, topic routed by {@code eventType}. Publish
 * failures are swallowed so they never fail the originating use case.
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

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String defaultTopic;
    private final String missionTopic;
    private final String staffTopic;
    private final String contractTopic;

    public KafkaAgencyEventPublisher(
            @Qualifier("agencyEventKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${tnt.kafka.topics.produced.staff-events:tnt.agency.staff.events}") String staffTopic,
            @Value("${tnt.kafka.topics.produced.contract-events:tnt.agency.contract.events}") String contractTopic,
            @Value("${tnt.kafka.topics.produced.mission-request:tnt.agency.mission.request}") String missionTopic,
            @Value("${tnt.kafka.topics.produced.domain-events:tnt.agency.events}") String defaultTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.staffTopic = staffTopic;
        this.contractTopic = contractTopic;
        this.missionTopic = missionTopic;
        this.defaultTopic = defaultTopic;
    }

    @Override
    public Mono<Void> publish(TntDomainEvent event) {
        String topic = resolveTopic(event);
        return Mono.fromCallable(() -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.convertValue(event, Map.class);
                    Map<String, Object> envelope = Map.of(
                            "eventId", event.getEventId().toString(),
                            "eventType", event.getEventType(),
                            "aggregateId", event.getAggregateId().toString(),
                            "aggregateType", event.getAggregateType(),
                            "tenantId", event.getTenantId().toString(),
                            "occurredAt", event.getOccurredAt().toString(),
                            "correlationId", event.getCorrelationId(),
                            "sequence", event.getSequenceNumber(),
                            "payload", payload
                    );
                    return kafkaTemplate.send(topic, event.getTenantId().toString(), envelope);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(r -> log.debug("[EVENT] published topic={} type={} aggregateId={}",
                        topic, event.getEventType(), event.getAggregateId()))
                .doOnError(e -> log.warn("[EVENT] publish failed type={} aggregateId={}: {}",
                        event.getEventType(), event.getAggregateId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    @Override
    public Mono<Void> publishAll(List<TntDomainEvent> events) {
        return Flux.fromIterable(events)
                .flatMap(this::publish)
                .then();
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
