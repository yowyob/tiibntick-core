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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Indexes entity change events into {@code entity_version} for delta pull.
 * Failures are not acked — {@link com.yowyob.tiibntick.core.sync.config.SyncKafkaConfig}
 * retries then publishes to {@code tnt.sync.entity-changed.dlq}.
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
            Map.entry("tnt.delivery.mission.status.changed", "MISSION"),
            Map.entry("tnt.delivery.package.updated", "PACKAGE"),
            Map.entry("tnt.actor.profile.updated", "ACTOR_PROFILE"),
            Map.entry("tnt.organization.hub.updated", "RELAY_HUB"),
            Map.entry("tnt.geo.alert.created", "GEO_ALERT"),
            Map.entry("tnt.realtime.geofence.triggered", "GEOFENCE_TRIGGER"),
            // ── Market — published by tnt-market-back-core's MarketKafkaEventPublisher,
            //    topic = "tnt.market." + toTopicSuffix(eventClassSimpleName). Lets a delta-pull
            //    pick up mutations made outside the sync engine (e.g. direct admin calls to
            //    /api/v1/platform/market/**) that never went through OfflineQueueDomainService. ──
            Map.entry("tnt.market.listing.published", MARKET_LISTING),
            Map.entry("tnt.market.listing.approved", MARKET_LISTING),
            Map.entry("tnt.market.listing.rejected", MARKET_LISTING),
            Map.entry("tnt.market.order.created", MARKET_ORDER),
            Map.entry("tnt.market.order.paid", MARKET_ORDER),
            Map.entry("tnt.market.order.completed", MARKET_ORDER),
            Map.entry("tnt.market.quote.request.created", MARKET_QUOTE_REQUEST),
            Map.entry("tnt.market.quote.response.submitted", MARKET_QUOTE_REQUEST),
            Map.entry("tnt.market.provider.review.published", MARKET_PROVIDER_REVIEW),
            Map.entry("tnt.market.merchant.contract.signed", MARKET_MERCHANT_CONTRACT)
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
                    "tnt.realtime.geofence.triggered",
                    "tnt.market.listing.published",
                    "tnt.market.listing.approved",
                    "tnt.market.listing.rejected",
                    "tnt.market.order.created",
                    "tnt.market.order.paid",
                    "tnt.market.order.completed",
                    "tnt.market.quote.request.created",
                    "tnt.market.quote.response.submitted",
                    "tnt.market.provider.review.published",
                    "tnt.market.merchant.contract.signed"
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
