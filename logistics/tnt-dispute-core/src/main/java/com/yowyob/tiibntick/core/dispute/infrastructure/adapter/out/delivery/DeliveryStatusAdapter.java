package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDeliveryStatusPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox-backed adapter implementing {@link IDeliveryStatusPort}.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * delegates to {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly via {@code KafkaTemplate}. Called only from
 * {@code DisputeCommandService}'s already-{@code @Transactional} use cases, so the DB write and
 * the outbox insert commit atomically.
 *
 * <p>The Kafka wire format is unchanged: the payload is still the same {@code Map} JSON, same
 * topics, same partition key (packageId) — only the transport changed.
 *
 * @author MANFOUO Braun
 */
@Component
public class DeliveryStatusAdapter implements IDeliveryStatusPort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStatusAdapter.class);
    private static final String TOPIC_PACKAGE_DISPUTED = "tnt.delivery.package.disputed";
    private static final String TOPIC_PACKAGE_DISPUTE_RELEASED = "tnt.delivery.package.dispute.released";
    private static final String AGGREGATE_TYPE = "Package";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public DeliveryStatusAdapter(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("disputeObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> markPackageAsDisputed(String packageId, String disputeId, String tenantId) {
        Map<String, Object> payload = Map.of(
                "packageId", packageId,
                "disputeId", disputeId,
                "tenantId", tenantId,
                "occurredAt", LocalDateTime.now().toString());
        return enqueue(TOPIC_PACKAGE_DISPUTED, packageId, tenantId, payload)
                .doOnSuccess(v -> log.info("Package marked as DISPUTED packageId={} disputeId={}", packageId, disputeId));
    }

    @Override
    public Mono<Void> releasePackageFromDispute(String packageId, String disputeId, String resolutionOutcome, String tenantId) {
        Map<String, Object> payload = Map.of(
                "packageId", packageId,
                "disputeId", disputeId,
                "resolutionOutcome", resolutionOutcome,
                "tenantId", tenantId,
                "occurredAt", LocalDateTime.now().toString());
        return enqueue(TOPIC_PACKAGE_DISPUTE_RELEASED, packageId, tenantId, payload)
                .doOnSuccess(v -> log.info("Package released from dispute packageId={} outcome={}", packageId, resolutionOutcome));
    }

    private Mono<Void> enqueue(String topic, String packageId, String tenantId, Map<String, Object> payload) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(topic)
                        .aggregateId(packageId)
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(tenantId)
                        .solutionCode(SOLUTION_CODE)
                        .payload(json)
                        .kafkaTopic(topic)
                        .kafkaPartitionKey(packageId)
                        .occurredAt(LocalDateTime.now(ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnError(e -> log.error("Failed to enqueue event to topic={}: {}", topic, e.getMessage()));
    }
}
