package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDeliveryStatusPort;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class DeliveryStatusAdapter implements IDeliveryStatusPort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStatusAdapter.class);
    private static final String TOPIC_PACKAGE_DISPUTED = "tnt.delivery.package.disputed";
    private static final String TOPIC_PACKAGE_DISPUTE_RELEASED = "tnt.delivery.package.dispute.released";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DeliveryStatusAdapter(
            @Qualifier("disputeKafkaProducerTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("disputeObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> markPackageAsDisputed(String packageId, String disputeId, String tenantId) {
        Map<String, Object> payload = Map.of(
                "packageId", packageId,
                "disputeId", disputeId,
                "tenantId", tenantId,
                "occurredAt", LocalDateTime.now().toString());
        return sendEvent(TOPIC_PACKAGE_DISPUTED, packageId, payload)
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
        return sendEvent(TOPIC_PACKAGE_DISPUTE_RELEASED, packageId, payload)
                .doOnSuccess(v -> log.info("Package released from dispute packageId={} outcome={}", packageId, resolutionOutcome));
    }

    private Mono<Void> sendEvent(String topic, String key, Map<String, Object> payload) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .flatMap(json -> Mono.fromFuture(
                        kafkaTemplate.send(new ProducerRecord<>(topic, key, json))))
                .doOnError(e -> log.error("Failed to send event to topic={}: {}", topic, e.getMessage()))
                .then();
    }
}