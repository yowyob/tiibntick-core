package com.yowyob.kernel.event.adapter.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.support.SendResult;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.out.KafkaPublisherPort;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka adapter implementing {@link KafkaPublisherPort}.
 *
 * <p>Uses the standard {@link KafkaTemplate} from Spring Kafka
 * to publish event payloads as UTF-8 strings. Kafka message headers are
 * automatically injected with Yowyob standard metadata so that consumers can
 * identify, trace and filter events without deserialising the payload.
 *
 * <p><strong>Headers injected:</strong>
 * <ul>
 *   <li>{@code X-Yow-Envelope-Id} — unique envelope identifier</li>
 *   <li>{@code X-Yow-Correlation-Id} — business correlation ID</li>
 *   <li>{@code X-Yow-Solution-Code} — originating solution (e.g. {@code TNT})</li>
 *   <li>{@code X-Yow-Event-Type} — fully qualified event type name</li>
 *   <li>{@code X-Yow-Aggregate-Id} — aggregate identifier</li>
 *   <li>{@code X-Yow-Aggregate-Type} — aggregate type discriminator</li>
 *   <li>{@code X-Yow-Tenant-Id} — tenant identifier</li>
 *   <li>{@code X-Yow-Schema-Version} — Avro schema version as ASCII decimal</li>
 *   <li>{@code X-Yow-Replay} — {@code "true"} only for replay messages</li>
 * </ul>
 */
@Component
public class KafkaEventPublisher implements KafkaPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    // Header name constants — kept as package-private for use in tests
    static final String HEADER_ENVELOPE_ID      = "X-Yow-Envelope-Id";
    static final String HEADER_CORRELATION_ID   = "X-Yow-Correlation-Id";
    static final String HEADER_SOLUTION_CODE    = "X-Yow-Solution-Code";
    static final String HEADER_EVENT_TYPE       = "X-Yow-Event-Type";
    static final String HEADER_AGGREGATE_ID     = "X-Yow-Aggregate-Id";
    static final String HEADER_AGGREGATE_TYPE   = "X-Yow-Aggregate-Type";
    static final String HEADER_TENANT_ID        = "X-Yow-Tenant-Id";
    static final String HEADER_SCHEMA_VERSION   = "X-Yow-Schema-Version";
    static final String HEADER_REPLAY           = "X-Yow-Replay";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaEventPublisher(
            @Qualifier("tntKafkaTemplate") final KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate);
    }

    // ── KafkaPublisherPort ───────────────────────────────────────────────────

    @Override
    public Mono<Void> publish(
            final DomainEventEnvelope envelope,
            final Map<String, String> extraHeaders) {
        ProducerRecord<String, String> record = buildRecord(envelope, extraHeaders, false);
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        return Mono.fromFuture(future)
            .doOnSuccess(result -> log.trace(
                "Sent envelope {} to {}@{}", envelope.getId().value(),
                result.getRecordMetadata().topic(), result.getRecordMetadata().offset()))
            .doOnError(error -> log.error(
                "Failed to send envelope {} to topic {}: {}",
                envelope.getId().value(), envelope.getKafkaTopic(), error.getMessage()))
            .then();
    }

    @Override
    public Mono<Void> publishAsReplay(final DomainEventEnvelope envelope) {
        ProducerRecord<String, String> record = buildRecord(envelope, Map.of(), true);
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        return Mono.fromFuture(future).then();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private ProducerRecord<String, String> buildRecord(
            final DomainEventEnvelope envelope,
            final Map<String, String> extraHeaders,
            final boolean isReplay) {

        RecordHeaders headers = new RecordHeaders();

        // Standard Yowyob headers
        addHeader(headers, HEADER_ENVELOPE_ID,    envelope.getId().value());
        addHeader(headers, HEADER_CORRELATION_ID, envelope.getCorrelationId());
        addHeader(headers, HEADER_SOLUTION_CODE,  envelope.getSolutionCode());
        addHeader(headers, HEADER_EVENT_TYPE,     envelope.getEventType());
        addHeader(headers, HEADER_AGGREGATE_ID,   envelope.getAggregateId());
        addHeader(headers, HEADER_AGGREGATE_TYPE, envelope.getAggregateType());
        addHeader(headers, HEADER_TENANT_ID,      envelope.getTenantId());
        addHeader(headers, HEADER_SCHEMA_VERSION, String.valueOf(envelope.getSchemaVersion()));

        if (isReplay) {
            addHeader(headers, HEADER_REPLAY, "true");
        }

        // Caller-supplied extra headers (may override standard ones — intentional)
        if (extraHeaders != null) {
            extraHeaders.forEach((key, value) -> addHeader(headers, key, value));
        }

        return new ProducerRecord<>(
            envelope.getKafkaTopic(),
            null,                           // partition — let Kafka decide based on key
            envelope.getKafkaPartitionKey(),
            envelope.getPayload(),
            headers
        );
    }

    private static void addHeader(
            final RecordHeaders headers,
            final String key,
            final String value) {
        if (value != null) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}