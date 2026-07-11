package com.yowyob.tiibntick.core.trust.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import com.yowyob.tiibntick.core.trust.application.port.out.CustodyTransferCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.DeliveryProofCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.DIDRepository;

/**
 * Kafka Adapter — {@code TrustCommittedEventConsumer}.
 *
 * <p>Listens to the {@code yow.trust.events.committed} topic published by
 * the {@code yow-trust-event} Kernel microservice after a successful Fabric commit.
 * Updates the local PostgreSQL cache with the Fabric transaction hash.
 *
 * <h3>Why this consumer is needed</h3>
 * <p>When {@code tnt-trust} publishes an event to Kafka, the Fabric commit
 * is asynchronous. The tx hash is not available immediately. This consumer
 * closes the loop by updating local records when the Kernel confirms the commit.
 *
 * <h3>Consumer Group</h3>
 * <p>{@code tnt-trust-committed-listener} — separate from the main writer group.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Component
public class TrustCommittedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrustCommittedEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final DeliveryProofCacheRepository proofCacheRepository;
    private final CustodyTransferCacheRepository custodyCacheRepository;
    private final DIDRepository didRepository;
    private final com.yowyob.tiibntick.core.trust.application.port.out.ActorBadgeRepository actorBadgeRepository;
    private final MeterRegistry meterRegistry;

    public TrustCommittedEventConsumer(
            final ObjectMapper objectMapper,
            final DeliveryProofCacheRepository proofCacheRepository,
            final CustodyTransferCacheRepository custodyCacheRepository,
            final DIDRepository didRepository,
            final com.yowyob.tiibntick.core.trust.application.port.out.ActorBadgeRepository actorBadgeRepository,
            final MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.proofCacheRepository = proofCacheRepository;
        this.custodyCacheRepository = custodyCacheRepository;
        this.didRepository = didRepository;
        this.actorBadgeRepository = actorBadgeRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Processes a committed event notification from {@code yow-trust-event}.
     * Updates the tx hash in the local cache for the corresponding entity.
     *
     * <p>Message payload structure:
     * <pre>
     * {
     *   "eventId":      "uuid",
     *   "correlationId":"uuid",
     *   "entityType":   "DELIVERY_PROOF",
     *   "entityId":     "uuid",
     *   "txHash":       "hex64",
     *   "solutionCode": "TNT",
     *   "outcome":      "COMMITTED"
     * }
     * </pre>
     */
    @KafkaListener(
            topics = "yow.trust.events.committed",
            groupId = "tnt-trust-committed-listener",
            containerFactory = "tntTrustKafkaListenerContainerFactory")
    public void onCommitted(
            @Payload final String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) final String topic,
            final Acknowledgment ack) {

        try {
            final JsonNode msg = objectMapper.readTree(payload);
            final String entityType = getField(msg, "entityType");
            final String entityId = getField(msg, "entityId");
            final String txHash = getField(msg, "txHash");
            final String solutionCode = getField(msg, "solutionCode");

            // Only process TNT events
            if (!"TNT".equals(solutionCode)) {
                ack.acknowledge();
                return;
            }

            log.info("Processing committed event — entityType={}, entityId={}, txHash={}",
                    entityType, entityId, txHash);

            updateLocalCache(entityType, entityId, txHash);

            meterRegistry.counter("tnt.trust.committed.processed",
                    "entityType", entityType).increment();

            ack.acknowledge();

        } catch (final Exception e) {
            log.error("Failed to process committed event: {}", e.getMessage());
            // Acknowledge to avoid blocking the partition — the cache miss is non-critical
            ack.acknowledge();
        }
    }

    /**
     * Routes the cache update to the appropriate repository based on entity type.
     */
    private void updateLocalCache(
            final String entityType, final String entityId, final String txHash) {
        switch (entityType) {
            case "DELIVERY_PROOF" ->
                    proofCacheRepository.updateTxHash(entityId, txHash)
                            .doOnSuccess(v -> log.debug("Updated delivery proof txHash — proofId={}", entityId))
                            .subscribe();

            case "CUSTODY_TRANSFER" ->
                    custodyCacheRepository.updateTxHash(entityId, txHash)
                            .doOnSuccess(v -> log.debug("Updated custody transfer txHash — transferId={}", entityId))
                            .subscribe();

            case "DID_DOCUMENT" ->
                    didRepository.findByDID(entityId)
                            .flatMap(doc -> {
                                doc.confirmOnChain(txHash);
                                return didRepository.save(doc);
                            })
                            .doOnSuccess(v -> log.debug("DID confirmed on-chain — did={}", entityId))
                            .subscribe();

            case "BADGE" ->
                    actorBadgeRepository.updateTxHash(entityId, txHash)
                            .doOnSuccess(v -> log.debug("Badge confirmed on-chain — badgeId={}", entityId))
                            .subscribe();

            default ->
                    log.debug("No local cache update needed for entityType={}", entityType);
        }
    }

    private String getField(final JsonNode node, final String field) {
        final JsonNode val = node.get(field);
        return val != null && !val.isNull() ? val.asText() : null;
    }
}
