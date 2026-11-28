package com.yowyob.tiibntick.core.sync.adapter.in.kafka;

import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntTopics;

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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Indexes entity change events into {@code entity_version} for delta pull.
 * Failures are not acked — the common error handler retries then publishes to {@code tnt.dlq} (lot L8).
 *
 * <p>Lot L8: the parcel, delivery, incident, dispute and announcement changes come from the five aggregate topics
 * ({@code tnt.<aggregate>.events}); the entity type is the envelope's {@code aggregateType} ({@code PARCEL},
 * {@code DELIVERY}, {@code INCIDENT}, {@code DISPUTE}, {@code ANNOUNCEMENT} — formerly {@code PACKAGE} /
 * {@code MISSION}), the stored change is the whole envelope (masked snapshot).
 */
@Component
public class EntityChangedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntityChangedEventConsumer.class);

    // ── Market aggregate-type literals ──
    // Mirrors com.yowyob.tiibntick.core.marketback.adapter.in.sync.MarketOfflineOperationApplier's
    // MARKET_* constants (tnt-market-back-core). Cannot import them directly: tnt-market-back-core
    // depends down into tnt-sync-core (see its pom.xml, added for IOfflineOperationApplier), so an
    // import in the other direction would be a circular Maven dependency. Keep these literals in
    // sync with that class if the aggregate-type vocabulary ever changes.
    // NOTE: MARKET_SERVICE_OFFER and MARKET_CAMPAIGN are omitted here — there is currently no
    // domain event published for those two aggregate types (see MarketKafkaEventPublisher's
    // callers), so no topic maps to them below. Add the literal here if/when one is introduced.
    private static final String MARKET_LISTING = "MARKET_LISTING";
    private static final String MARKET_QUOTE_REQUEST = "MARKET_QUOTE_REQUEST";
    private static final String MARKET_ORDER = "MARKET_ORDER";
    private static final String MARKET_PROVIDER_REVIEW = "MARKET_PROVIDER_REVIEW";
    private static final String MARKET_MERCHANT_CONTRACT = "MARKET_MERCHANT_CONTRACT";

    private static final Map<String, String> TOPIC_TO_AGGREGATE = Map.ofEntries(
            Map.entry(TntTopics.PARCEL_EVENTS, "PARCEL"),
            Map.entry(TntTopics.DELIVERY_EVENTS, "DELIVERY"),
            Map.entry(TntTopics.INCIDENT_EVENTS, "INCIDENT"),
            Map.entry(TntTopics.DISPUTE_EVENTS, "DISPUTE"),
            Map.entry(TntTopics.ANNOUNCEMENT_EVENTS, "ANNOUNCEMENT"),
            Map.entry(TntTopics.ACTOR_PROFILE_UPDATED, "ACTOR_PROFILE"),
            Map.entry(TntTopics.ORGANIZATION_HUB_UPDATED, "RELAY_HUB"),
            Map.entry(TntTopics.GEO_ALERT_CREATED, "GEO_ALERT"),
            Map.entry(TntTopics.REALTIME_GEOFENCE_TRIGGERED, "GEOFENCE_TRIGGER"),
            // ── Market — published by tnt-market-back-core's MarketKafkaEventPublisher,
            //    one TntTopics constant per event class (lot L8, was a concatenated name). Lets a delta-pull
            //    pick up mutations made outside the sync engine (e.g. direct admin calls to
            //    /api/v1/platform/market/**) that never went through OfflineQueueDomainService. ──
            Map.entry(TntTopics.MARKET_LISTING_PUBLISHED, MARKET_LISTING),
            Map.entry(TntTopics.MARKET_LISTING_APPROVED, MARKET_LISTING),
            Map.entry(TntTopics.MARKET_LISTING_REJECTED, MARKET_LISTING),
            Map.entry(TntTopics.MARKET_ORDER_CREATED, MARKET_ORDER),
            Map.entry(TntTopics.MARKET_ORDER_PAID, MARKET_ORDER),
            Map.entry(TntTopics.MARKET_ORDER_COMPLETED, MARKET_ORDER),
            Map.entry(TntTopics.MARKET_QUOTE_REQUEST_CREATED, MARKET_QUOTE_REQUEST),
            Map.entry(TntTopics.MARKET_QUOTE_RESPONSE_SUBMITTED, MARKET_QUOTE_REQUEST),
            Map.entry(TntTopics.MARKET_PROVIDER_REVIEW_PUBLISHED, MARKET_PROVIDER_REVIEW),
            Map.entry(TntTopics.MARKET_MERCHANT_CONTRACT_SIGNED, MARKET_MERCHANT_CONTRACT)
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
                    TntTopics.PARCEL_EVENTS,
                    TntTopics.DELIVERY_EVENTS,
                    TntTopics.INCIDENT_EVENTS,
                    TntTopics.DISPUTE_EVENTS,
                    TntTopics.ANNOUNCEMENT_EVENTS,
                    TntTopics.ACTOR_PROFILE_UPDATED,
                    TntTopics.ORGANIZATION_HUB_UPDATED,
                    TntTopics.GEO_ALERT_CREATED,
                    TntTopics.REALTIME_GEOFENCE_TRIGGERED,
                    TntTopics.MARKET_LISTING_PUBLISHED,
                    TntTopics.MARKET_LISTING_APPROVED,
                    TntTopics.MARKET_LISTING_REJECTED,
                    TntTopics.MARKET_ORDER_CREATED,
                    TntTopics.MARKET_ORDER_PAID,
                    TntTopics.MARKET_ORDER_COMPLETED,
                    TntTopics.MARKET_QUOTE_REQUEST_CREATED,
                    TntTopics.MARKET_QUOTE_RESPONSE_SUBMITTED,
                    TntTopics.MARKET_PROVIDER_REVIEW_PUBLISHED,
                    TntTopics.MARKET_MERCHANT_CONTRACT_SIGNED
            },
            groupId = "${tnt.kafka.consumer.groups.sync:tnt-sync-core}",
            containerFactory = "syncKafkaListenerContainerFactory"
    )
    public void onEntityChanged(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String topic = record.topic();
        String aggregateType = TOPIC_TO_AGGREGATE.getOrDefault(topic, "UNKNOWN");

        log.trace("Received entity change event from topic={}, key={}", topic, record.key());

        try {
            JsonNode node = objectMapper.readTree(record.value());
            if (AGGREGATE_TOPICS.contains(topic)) {
                if (!TntAggregateEvent.isAggregateEvent(node)) {
                    log.debug("Record without schemaVersion on {} ignored (spec §3.9)", topic);
                    acknowledgment.acknowledge();
                    return;
                }
                indexAggregateEvent(node, aggregateType, record.value());
                acknowledgment.acknowledge();
                return;
            }
            String tenantId = extractField(node, "tenantId", "default");
            String aggregateId = record.key() != null
                    ? record.key()
                    : extractField(node, "id", extractField(node, "missionId", "unknown"));
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
                    .block(Duration.ofSeconds(30));
            acknowledgment.acknowledge();
        } catch (RuntimeException e) {
            log.error("Failed to process entity change event from topic={} key={}: {}",
                    topic, record.key(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to process entity change event from topic={} key={}: {}",
                    topic, record.key(), e.getMessage(), e);
            throw new IllegalStateException("Entity change indexing failed", e);
        }
    }

    private static final java.util.Set<String> AGGREGATE_TOPICS = java.util.Set.of(TntTopics.PARCEL_EVENTS,
            TntTopics.DELIVERY_EVENTS, TntTopics.INCIDENT_EVENTS, TntTopics.DISPUTE_EVENTS,
            TntTopics.ANNOUNCEMENT_EVENTS);

    /** Creation event (no previous state) → CREATED; status change → STATUS_CHANGED; other change → UPDATED. */
    private void indexAggregateEvent(JsonNode event, String aggregateType, String value) {
        JsonNode previous = event.path("previousState");
        String status = event.path("payload").path("status").asText(null);
        DeltaOperation op = previous.isMissingNode() || previous.isNull() ? DeltaOperation.CREATED
                : status != null && !status.equals(previous.path("status").asText(null))
                ? DeltaOperation.STATUS_CHANGED : DeltaOperation.UPDATED;
        JsonNode actor = event.path("actor");
        String by = actor.hasNonNull("id") ? actor.path("type").asText("SYSTEM") + ":" + actor.path("id").asText()
                : actor.path("type").asText("system");
        entityVersionRepository.upsert(new EntityVersionRecord(
                        extractField(event, "tenantId", "default"), aggregateType,
                        event.path("aggregateId").asText(), System.currentTimeMillis(), op, value,
                        LocalDateTime.now(), by))
                .block(Duration.ofSeconds(30));
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
