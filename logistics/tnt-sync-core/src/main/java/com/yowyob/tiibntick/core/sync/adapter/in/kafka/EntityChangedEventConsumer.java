package com.yowyob.tiibntick.core.sync.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository;
import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kafka consumer that indexes entity change events from all other TiiBnTick Core modules
 * into the entity_version table. This is the server-side change log used by
 * DeltaSyncDomainService to answer client delta-pull requests.
 *
 * Consumed topics (from tnt-delivery-core, tnt-actor-core, tnt-organization-core, etc.):
 * - tnt.delivery.mission.status.changed
 * - tnt.delivery.package.updated
 * - tnt.actor.profile.updated
 * - tnt.organization.hub.updated
 * - tnt.geo.alert.created (for network alerts in TiiBnTick Link)
 *
 * Author: MANFOUO Braun
 */
@Component
public class EntityChangedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntityChangedEventConsumer.class);

    private static final Map<String, String> TOPIC_TO_AGGREGATE = Map.of(
            "tnt.delivery.mission.status.changed", "MISSION",
            "tnt.delivery.package.updated", "PACKAGE",
            "tnt.actor.profile.updated", "ACTOR_PROFILE",
            "tnt.organization.hub.updated", "RELAY_HUB",
            "tnt.geo.alert.created", "GEO_ALERT",
            "tnt.realtime.geofence.triggered", "GEOFENCE_TRIGGER"
    );

    private final IEntityVersionRepository entityVersionRepository;
    private final ObjectMapper objectMapper;

    public EntityChangedEventConsumer(IEntityVersionRepository entityVersionRepository,
                                      @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.entityVersionRepository = entityVersionRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    "tnt.delivery.mission.status.changed",
                    "tnt.delivery.package.updated",
                    "tnt.actor.profile.updated",
                    "tnt.organization.hub.updated",
                    "tnt.geo.alert.created",
                    "tnt.realtime.geofence.triggered"
            },
            groupId = "${spring.kafka.consumer.group-id:tnt-sync-core}",
            containerFactory = "syncKafkaListenerContainerFactory"
    )
    public void onEntityChanged(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String topic = record.topic();
        String aggregateType = TOPIC_TO_AGGREGATE.getOrDefault(topic, "UNKNOWN");

        log.trace("Received entity change event from topic={}, key={}", topic, record.key());

        try {
            JsonNode node = objectMapper.readTree(record.value());
            String tenantId = extractField(node, "tenantId", "default");
            String aggregateId = record.key() != null ? record.key() : extractField(node, "id", extractField(node, "missionId", "unknown"));
            String operation = extractField(node, "operation", "UPDATED");

            DeltaOperation deltaOp = parseDeltaOperation(operation, topic);

            EntityVersionRecord versionRecord = new EntityVersionRecord(
                    tenantId, aggregateType, aggregateId,
                    System.currentTimeMillis(),
                    deltaOp,
                    record.value(),
                    LocalDateTime.now(),
                    extractField(node, "userId", extractField(node, "updatedBy", "system"))
            );

            entityVersionRepository.upsert(versionRecord)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnSuccess(v -> acknowledgment.acknowledge())
                    .doOnError(ex -> {
                        log.error("Failed to record entity version change for {}/{}: {}",
                                aggregateType, aggregateId, ex.getMessage());
                        acknowledgment.acknowledge();
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Failed to process entity change event from topic={}: {}", topic, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private String extractField(JsonNode node, String field, String defaultValue) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : defaultValue;
    }

    private DeltaOperation parseDeltaOperation(String rawOp, String topic) {
        if (topic.contains("created") || topic.contains("geo.alert")) return DeltaOperation.CREATED;
        if (topic.contains("deleted")) return DeltaOperation.DELETED;
        if (topic.contains("status")) return DeltaOperation.STATUS_CHANGED;
        return DeltaOperation.UPDATED;
    }
}
